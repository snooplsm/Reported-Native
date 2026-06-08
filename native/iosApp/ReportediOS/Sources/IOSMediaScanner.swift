import Foundation
import CryptoKit
import ImageIO
import Photos
import SharedCore
import UIKit
import UniformTypeIdentifiers
import UserNotifications

private let reportedMediaScannerLogTag = "ReportedMediaScanner"

enum IOSMediaScannerSettings {
    private static let notificationsEnabledKey = "reported.ios.mediaScanner.notificationsEnabled"
    private static let scannerEnabledKey = "reported.ios.mediaScanner.enabled"
    private static let offlineProcessingEnabledKey = "reported.ios.mediaScanner.offlineProcessingEnabled"
    private static let lastScanDateKey = "reported.ios.mediaScanner.lastScanDate"
    private static let seenAssetIdsKey = "reported.ios.mediaScanner.seenAssetIds"

    static var notificationsEnabled: Bool {
        get { UserDefaults.standard.object(forKey: notificationsEnabledKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: notificationsEnabledKey) }
    }

    static var isEnabled: Bool {
        get {
            RemoteConfigOverrides.shared.enableMediaScanner &&
                (UserDefaults.standard.object(forKey: scannerEnabledKey) as? Bool ?? false)
        }
        set {
            UserDefaults.standard.set(newValue && RemoteConfigOverrides.shared.enableMediaScanner, forKey: scannerEnabledKey)
        }
    }

    static var isOfflineProcessingEnabled: Bool {
        get {
            RemoteConfigOverrides.shared.enableOfflinePhotoProcessing &&
                (UserDefaults.standard.object(forKey: offlineProcessingEnabledKey) as? Bool ?? false)
        }
        set {
            UserDefaults.standard.set(newValue && RemoteConfigOverrides.shared.enableOfflinePhotoProcessing, forKey: offlineProcessingEnabledKey)
        }
    }

    static var lastScanDate: Date? {
        get { UserDefaults.standard.object(forKey: lastScanDateKey) as? Date }
        set { UserDefaults.standard.set(newValue, forKey: lastScanDateKey) }
    }

    static func hasSeen(_ id: String) -> Bool {
        seenAssetIds.contains(id)
    }

    static func markSeen(_ id: String) {
        var ids = seenAssetIds
        ids.insert(id)
        if ids.count > 700 {
            ids = Set(Array(ids).suffix(500))
        }
        UserDefaults.standard.set(Array(ids), forKey: seenAssetIdsKey)
    }

    private static var seenAssetIds: Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: seenAssetIdsKey) ?? [])
    }

    static func effectiveNotificationsEnabled() async -> Bool {
        guard notificationsEnabled else { return false }
        return await systemNotificationsAuthorized()
    }

    @discardableResult
    static func requestNotificationsIfNeeded() async -> Bool {
        notificationsEnabled = true
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined:
            _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            return await systemNotificationsAuthorized()
        case .denied:
            return false
        @unknown default:
            return false
        }
    }

    static func systemNotificationsAuthorized() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        default:
            return false
        }
    }

    static var photoAccessGranted: Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    @discardableResult
    static func requestPhotoAccessIfNeeded() async -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if status == .authorized || status == .limited {
            return true
        }
        guard status == .notDetermined else {
            return false
        }
        let finalStatus = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return finalStatus == .authorized || finalStatus == .limited
    }
}

struct IOSDetectedInfraction: Codable {
    struct StoredCandidate: Codable {
        let plate: String
        let confidence: Double
        let rawPlateText: String?
        let wasPlateCorrected: Bool?
        let state: String?
        let stateConfidence: Double?
        let plateType: String?
        let plateTypeLabel: String?
        let boundsMinX: Double?
        let boundsMinY: Double?
        let boundsMaxX: Double?
        let boundsMaxY: Double?

        init(_ candidate: ComposerState.PlateCandidate) {
            plate = candidate.plate
            confidence = candidate.confidence
            rawPlateText = candidate.rawPlateText
            wasPlateCorrected = candidate.wasPlateCorrected
            state = candidate.state
            stateConfidence = candidate.stateConfidence
            plateType = candidate.plateType
            plateTypeLabel = candidate.plateTypeLabel
            boundsMinX = candidate.bounds.map { Double($0.minX) }
            boundsMinY = candidate.bounds.map { Double($0.minY) }
            boundsMaxX = candidate.bounds.map { Double($0.maxX) }
            boundsMaxY = candidate.bounds.map { Double($0.maxY) }
        }

        func toDraft() -> DraftPlateCandidate {
            DraftPlateCandidate(
                plate: plate,
                confidence: Float(confidence),
                rawPlateText: rawPlateText,
                wasPlateCorrected: wasPlateCorrected ?? false,
                state: state,
                stateConfidence: stateConfidence.map { KotlinFloat(float: Float($0)) },
                plateType: plateType,
                plateTypeLabel: plateTypeLabel,
                focalPointX: nil,
                focalPointY: nil,
                boundsLeft: boundsMinX.map { KotlinFloat(float: Float($0)) },
                boundsTop: boundsMinY.map { KotlinFloat(float: Float($0)) },
                boundsRight: boundsMaxX.map { KotlinFloat(float: Float($0)) },
                boundsBottom: boundsMaxY.map { KotlinFloat(float: Float($0)) },
                rotationDegrees: 0,
                cornerPoints: [],
                sourceImageWidth: nil,
                sourceImageHeight: nil,
                thumbnailUri: nil,
                videoFramePreviewUri: nil,
                videoFrameTimeMs: nil
            )
        }
    }

    let id: String
    let mediaURL: URL
    let displayName: String
    let plate: String
    let plateRegion: String
    let plateConfidence: Double
    let stateConfidence: Double
    let complaintId: String
    let complaintConfidence: Double?
    let occurredAtIso: String
    let address: String
    let latitude: Double?
    let longitude: Double?
    let contentHash: String?
    let candidates: [StoredCandidate]

    var media: ComposerState.SubmissionMedia {
        ComposerState.SubmissionMedia(fileURL: mediaURL, displayName: displayName, isVideo: false)
    }

    func toDraft() -> ReportDraft {
        ReportDraft(
            plate: plate,
            plateRegion: plateRegion,
            address: address,
            description: "",
            notes: "",
            complaintIds: [complaintId],
            occurredAtIso: occurredAtIso,
            selectedComplaintId: complaintId,
            stage: "VERIFY",
            primaryMedia: DraftMedia(
                uri: mediaURL.absoluteString,
                displayName: displayName,
                mimeType: "image/jpeg",
                isVideo: false
            ),
            extraMedia: [],
            latitude: latitude.map { KotlinDouble(double: $0) },
            longitude: longitude.map { KotlinDouble(double: $0) },
            plateCandidates: candidates.map { $0.toDraft() },
            selectedPlateCandidate: plate,
            vehicleImageDescription: nil,
            vehicleColor: nil,
            vehicleMake: nil,
            vehicleModel: nil
        )
    }
}

enum IOSDetectedInfractionStore {
    private static let directoryName = "DetectedInfractions"

    static func save(_ infraction: IOSDetectedInfraction) {
        do {
            let data = try JSONEncoder().encode(infraction)
            try data.write(to: try url(for: infraction.id), options: .atomic)
        } catch {
            print("\(reportedMediaScannerLogTag): failed saving detected infraction \(infraction.id): \(error)")
        }
    }

    static func load(id: String) -> IOSDetectedInfraction? {
        guard let data = try? Data(contentsOf: try url(for: id)) else { return nil }
        return try? JSONDecoder().decode(IOSDetectedInfraction.self, from: data)
    }

    static func remove(id: String) {
        try? FileManager.default.removeItem(at: try url(for: id))
    }

    private static func url(for id: String) throws -> URL {
        try directoryURL().appendingPathComponent(sanitized(id)).appendingPathExtension("json")
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

    private static func sanitized(_ value: String) -> String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        let scalars = value.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" }
        return String(scalars)
    }
}

struct IOSAutoReportScanSummary {
    let photoAccessGranted: Bool
    let scannedCount: Int
    let matches: [IOSDetectedInfraction]
    let processedPhotos: [IOSAutoReportProcessedPhoto]

    var groups: [IOSAutoReportGroup] {
        Dictionary(grouping: matches, by: \.complaintId)
            .map { complaintId, infractions in
                IOSAutoReportGroup(
                    complaintId: complaintId,
                    title: notificationComplaintLabel(for: complaintId),
                    infractions: infractions.sorted { $0.occurredAtIso < $1.occurredAtIso }
                )
            }
            .sorted { $0.title < $1.title }
    }
}

struct IOSAutoReportProcessedPhoto: Identifiable {
    let id: String
    let mediaURL: URL?
    let resultTitle: String
    let resultDetail: String
    let matched: Bool
}

struct IOSAutoReportGroup: Identifiable {
    var id: String { complaintId }
    let complaintId: String
    let title: String
    let infractions: [IOSDetectedInfraction]
}

final class IOSMediaScanner: NSObject, PHPhotoLibraryChangeObserver, UNUserNotificationCenterDelegate {
    static let shared = IOSMediaScanner()

    private let notificationCategory = "reported.detected.infraction"
    private let actionSubmitNow = "reported.detected.submitNow"
    private let actionEdit = "reported.detected.edit"
    private let actionCancel = "reported.detected.cancel"
    private let candidateIdKey = "candidateId"
    private var registeredForPhotoChanges = false
    private var scanTask: Task<Void, Never>?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    func start() {
        configureNotifications()
        Task {
            guard IOSMediaScannerSettings.isEnabled else { return }
            guard IOSMediaScannerSettings.photoAccessGranted else {
                print("\(reportedMediaScannerLogTag): startup scan deferred: photo permission missing")
                return
            }
            registerForPhotoChangesIfNeeded()
            await scheduleScan(reason: "startup")
        }
    }

    @discardableResult
    func enableScannerFromSettings() async -> Bool {
        IOSMediaScannerSettings.isEnabled = true
        await requestPhotoAuthorizationAndRegister()
        guard IOSMediaScannerSettings.photoAccessGranted else {
            return false
        }
        await scheduleScan(reason: "settings")
        return true
    }

    func disableScannerFromSettings() {
        IOSMediaScannerSettings.isEnabled = false
        scanTask?.cancel()
        scanTask = nil
        if registeredForPhotoChanges {
            PHPhotoLibrary.shared().unregisterChangeObserver(self)
            registeredForPhotoChanges = false
        }
    }

    func scanNowFromSettings() {
        Task {
            await requestPhotoAuthorizationAndRegister()
            await scheduleScan(reason: "settings")
        }
    }

    func scanWhileBackgrounded() {
        beginBackgroundTask()
        Task {
            await scheduleScan(reason: "background")
        }
    }

    func runAutoReportScan(
        lookback: TimeInterval = 7 * 24 * 60 * 60,
        progress: @escaping @MainActor (_ processed: Int, _ total: Int) -> Void
    ) async -> IOSAutoReportScanSummary {
        guard await IOSMediaScannerSettings.requestPhotoAccessIfNeeded() else {
            return IOSAutoReportScanSummary(photoAccessGranted: false, scannedCount: 0, matches: [], processedPhotos: [])
        }
        registerForPhotoChangesIfNeeded()
        let since = Date(timeIntervalSinceNow: -lookback)
        let assets = fetchImageAssets(after: since, limit: nil)
        await progress(0, assets.count)
        var matches: [IOSDetectedInfraction] = []
        var processedPhotos: [IOSAutoReportProcessedPhoto] = []
        for (index, asset) in assets.enumerated() {
            guard !Task.isCancelled else { break }
            let result = await processAutoReport(asset: asset)
            processedPhotos.append(result.photo)
            if let detected = result.detected,
               autoReportComplaintIds.contains(detected.complaintId) {
                matches.append(detected)
            }
            await progress(index + 1, assets.count)
        }
        return IOSAutoReportScanSummary(
            photoAccessGranted: true,
            scannedCount: assets.count,
            matches: matches,
            processedPhotos: processedPhotos
        )
    }

    func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task {
            await scheduleScan(reason: "photo-library-change")
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let candidateId = response.notification.request.content.userInfo[candidateIdKey] as? String
        Task {
            defer { completionHandler() }
            guard let candidateId else { return }
            switch response.actionIdentifier {
            case UNNotificationDefaultActionIdentifier, actionEdit:
                await openForEditing(candidateId: candidateId)
            case actionSubmitNow:
                await submit(candidateId: candidateId)
            case actionCancel, UNNotificationDismissActionIdentifier:
                IOSDetectedInfractionStore.remove(id: candidateId)
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [candidateId])
                UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [candidateId])
            default:
                break
            }
        }
    }

    private func configureNotifications() {
        let submit = UNNotificationAction(
            identifier: actionSubmitNow,
            title: "Submit now",
            options: [.authenticationRequired]
        )
        let edit = UNNotificationAction(
            identifier: actionEdit,
            title: "Edit",
            options: [.foreground]
        )
        let cancel = UNNotificationAction(
            identifier: actionCancel,
            title: "Cancel",
            options: [.destructive]
        )
        let category = UNNotificationCategory(
            identifier: notificationCategory,
            actions: [submit, edit, cancel],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
        UNUserNotificationCenter.current().delegate = self
    }

    private func requestPhotoAuthorizationAndRegister() async {
        guard IOSMediaScannerSettings.isEnabled else { return }
        guard await IOSMediaScannerSettings.requestPhotoAccessIfNeeded() else {
            let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            print("\(reportedMediaScannerLogTag): photo library access not available: \(status.rawValue)")
            return
        }
        registerForPhotoChangesIfNeeded()
    }

    private func registerForPhotoChangesIfNeeded() {
        if !registeredForPhotoChanges {
            PHPhotoLibrary.shared().register(self)
            registeredForPhotoChanges = true
        }
    }

    private func scheduleScan(reason: String) async {
        scanTask?.cancel()
        scanTask = Task {
            try? await Task.sleep(nanoseconds: 900_000_000)
            guard !Task.isCancelled else { return }
            await scanRecentPhotos(reason: reason)
        }
    }

    private func scanRecentPhotos(reason: String) async {
        print("\(reportedMediaScannerLogTag): media scan started reason=\(reason)")
        guard IOSMediaScannerSettings.isEnabled else {
            print("\(reportedMediaScannerLogTag): skipping scan: scanner disabled")
            endBackgroundTask()
            return
        }
        guard IOSMediaScannerSettings.isOfflineProcessingEnabled else {
            print("\(reportedMediaScannerLogTag): skipping scan: offline processing disabled")
            endBackgroundTask()
            return
        }
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else {
            print("\(reportedMediaScannerLogTag): skipping scan: photo permission missing")
            endBackgroundTask()
            return
        }

        let scanStart = Date()
        let previousScan = IOSMediaScannerSettings.lastScanDate ?? Date(timeIntervalSinceNow: -6 * 60 * 60)
        let assets = fetchImageAssets(after: previousScan)
        print("\(reportedMediaScannerLogTag): found \(assets.count) photo asset(s) since \(previousScan)")
        for asset in assets {
            guard !Task.isCancelled else { break }
            if IOSMediaScannerSettings.hasSeen(asset.localIdentifier) {
                print("\(reportedMediaScannerLogTag): skipping already-seen asset \(asset.localIdentifier)")
                continue
            }
            IOSMediaScannerSettings.markSeen(asset.localIdentifier)
            _ = await process(asset: asset)
        }
        IOSMediaScannerSettings.lastScanDate = scanStart
        print("\(reportedMediaScannerLogTag): media scan finished")
        endBackgroundTask()
    }

    private func fetchImageAssets(after date: Date, limit: Int? = 30) -> [PHAsset] {
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
        options.predicate = NSPredicate(
            format: "mediaType == %d AND creationDate > %@",
            PHAssetMediaType.image.rawValue,
            date as NSDate
        )
        if let limit {
            options.fetchLimit = limit
        }
        let fetch = PHAsset.fetchAssets(with: options)
        var assets: [PHAsset] = []
        fetch.enumerateObjects { asset, _, _ in
            assets.append(asset)
        }
        return assets
    }

    private func process(asset: PHAsset, notify: Bool = true) async -> IOSDetectedInfraction? {
        print("\(reportedMediaScannerLogTag): processing asset \(asset.localIdentifier)")
        guard let media = await copyImageAsset(asset) else {
            print("\(reportedMediaScannerLogTag): skipping asset: unable to copy image data")
            return nil
        }
        let candidates = await NativeAlprEngine.shared.detectLicensePlates(media: media)
        print("\(reportedMediaScannerLogTag): detected \(candidates.count) plate candidate(s)")
        guard let bestPlate = candidates.bestMediaScannerCandidate() else {
            print("\(reportedMediaScannerLogTag): skipping asset: no plate met confidence/state threshold")
            return nil
        }
        print(
            "\(reportedMediaScannerLogTag): best plate \(bestPlate.plate) state=\(bestPlate.state ?? "") plateConfidence=\(bestPlate.confidence) stateConfidence=\(bestPlate.stateConfidence ?? 0)"
        )
        guard let complaintId = await NativeAlprEngine.shared.inferComplaintId(media: media) else {
            print("\(reportedMediaScannerLogTag): skipping asset: complaint inference did not meet threshold")
            return nil
        }
        let metadata = await extractSubmissionMetadata(from: media)
        let latitude = metadata.latitude ?? asset.location?.coordinate.latitude
        let longitude = metadata.longitude ?? asset.location?.coordinate.longitude
        let address = await ifLet(latitude, longitude) { lat, lon in
            await IOSMediaScanner.reverseGeocode(latitude: lat, longitude: lon)
        } ?? ""
        let occurredAtIso = metadata.occurredAtIso ?? asset.creationDate.map { scannerIsoStringUTC(from: $0) } ?? scannerIsoStringUTC(from: Date())
        let contentHash = mediaContentHash(for: media.fileURL)
        let plateRegion = bestPlate.state ?? "NY"
        let detectedId = detectedInfractionId(
            plate: bestPlate.plate,
            state: plateRegion,
            occurredAtIso: occurredAtIso
        )
        let detected = IOSDetectedInfraction(
            id: detectedId,
            mediaURL: media.fileURL,
            displayName: media.displayName,
            plate: bestPlate.plate,
            plateRegion: plateRegion,
            plateConfidence: bestPlate.confidence,
            stateConfidence: bestPlate.stateConfidence ?? 0,
            complaintId: complaintId,
            complaintConfidence: nil,
            occurredAtIso: occurredAtIso,
            address: address,
            latitude: latitude,
            longitude: longitude,
            contentHash: contentHash,
            candidates: candidates.map(IOSDetectedInfraction.StoredCandidate.init)
        )
        IOSDetectedInfractionStore.save(detected)
        if notify {
            await showDetectedNotification(detected)
        }
        return detected
    }

    private func processAutoReport(asset: PHAsset) async -> (photo: IOSAutoReportProcessedPhoto, detected: IOSDetectedInfraction?) {
        print("\(reportedMediaScannerLogTag): auto-report processing asset \(asset.localIdentifier)")
        guard let media = await copyImageAsset(asset) else {
            return (
                IOSAutoReportProcessedPhoto(
                    id: asset.localIdentifier,
                    mediaURL: nil,
                    resultTitle: "Could not read photo",
                    resultDetail: "Photo data was unavailable to the on-device scanner.",
                    matched: false
                ),
                nil
            )
        }

        let candidates = await NativeAlprEngine.shared.detectLicensePlates(media: media)
        guard let bestPlate = candidates.bestMediaScannerCandidate() else {
            return (
                IOSAutoReportProcessedPhoto(
                    id: asset.localIdentifier,
                    mediaURL: media.fileURL,
                    resultTitle: candidates.isEmpty ? "No vehicle plate found" : "No qualified vehicle plate",
                    resultDetail: candidates.isEmpty
                        ? "Plate AI found no readable plate candidates."
                        : "Top plate AI: \(candidates.bestObservedMediaScannerCandidate()?.mediaScannerConfidenceSummary ?? "\(candidates.count) candidate\(candidates.count == 1 ? "" : "s") found").",
                    matched: false
                ),
                nil
            )
        }

        let complaintInferenceResult = await NativeAlprEngine.shared.inferComplaint(media: media)
        guard let complaintInference = complaintInferenceResult,
              complaintInference.accepted,
              autoReportComplaintIds.contains(complaintInference.complaintId) else {
            return (
                IOSAutoReportProcessedPhoto(
                    id: asset.localIdentifier,
                    mediaURL: media.fileURL,
                    resultTitle: "Not report-worthy",
                    resultDetail: "Plate AI passed: \(bestPlate.mediaScannerConfidenceSummary). \(complaintInferenceResult.autoReportComplaintConfidenceSummary(matched: false))",
                    matched: false
                ),
                nil
            )
        }
        let complaintId = complaintInference.complaintId

        let metadata = await extractSubmissionMetadata(from: media)
        let latitude = metadata.latitude ?? asset.location?.coordinate.latitude
        let longitude = metadata.longitude ?? asset.location?.coordinate.longitude
        let address = await ifLet(latitude, longitude) { lat, lon in
            await IOSMediaScanner.reverseGeocode(latitude: lat, longitude: lon)
        } ?? ""
        let occurredAtIso = metadata.occurredAtIso ?? asset.creationDate.map { scannerIsoStringUTC(from: $0) } ?? scannerIsoStringUTC(from: Date())
        let contentHash = mediaContentHash(for: media.fileURL)
        let plateRegion = bestPlate.state ?? "NY"
        let detectedId = detectedInfractionId(
            plate: bestPlate.plate,
            state: plateRegion,
            occurredAtIso: occurredAtIso
        )
        let detected = IOSDetectedInfraction(
            id: detectedId,
            mediaURL: media.fileURL,
            displayName: media.displayName,
            plate: bestPlate.plate,
            plateRegion: plateRegion,
            plateConfidence: bestPlate.confidence,
            stateConfidence: bestPlate.stateConfidence ?? 0,
            complaintId: complaintId,
            complaintConfidence: Double(complaintInference.confidence),
            occurredAtIso: occurredAtIso,
            address: address,
            latitude: latitude,
            longitude: longitude,
            contentHash: contentHash,
            candidates: candidates.map(IOSDetectedInfraction.StoredCandidate.init)
        )
        IOSDetectedInfractionStore.save(detected)
        return (
            IOSAutoReportProcessedPhoto(
                id: asset.localIdentifier,
                mediaURL: media.fileURL,
                resultTitle: "Matched \(notificationComplaintLabel(for: complaintId))",
                resultDetail: "Plate AI passed: \(bestPlate.mediaScannerConfidenceSummary). \(Optional(complaintInference).autoReportComplaintConfidenceSummary(matched: true))",
                matched: true
            ),
            detected
        )
    }

    private func copyImageAsset(_ asset: PHAsset) async -> ComposerState.SubmissionMedia? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .highQualityFormat
            options.isNetworkAccessAllowed = true
            options.version = .current
            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, dataUti, _, _ in
                guard let data else {
                    continuation.resume(returning: nil)
                    return
                }
                let ext = (dataUti.flatMap { UTType($0)?.preferredFilenameExtension }) ?? "jpg"
                let preferredName = "scanner-\(asset.localIdentifier.replacingOccurrences(of: "/", with: "-"))"
                do {
                    let url = try PersistentMediaStore.saveReplacing(
                        data: data,
                        preferredName: preferredName,
                        fileExtension: ext
                    )
                    continuation.resume(returning: ComposerState.SubmissionMedia(
                        fileURL: url,
                        displayName: url.lastPathComponent,
                        isVideo: false
                    ))
                } catch {
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    private func mediaContentHash(for url: URL) -> String? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return SHA256.hash(data: data)
            .map { String(format: "%02x", $0) }
            .joined()
    }

    private func showDetectedNotification(_ infraction: IOSDetectedInfraction) async {
        guard await IOSMediaScannerSettings.effectiveNotificationsEnabled() else {
            print("\(reportedMediaScannerLogTag): skipping detection notification: notifications disabled")
            return
        }
        let content = UNMutableNotificationContent()
        let complaintLabel = notificationComplaintLabel(for: infraction.complaintId)
        let incidentTime = infraction.occurredAtIso.detectedNotificationTime
        content.title = "\(complaintLabel) detected"
        content.body = "Incident time: \(incidentTime)" + (infraction.address.isEmpty ? "" : " • \(infraction.address)")
        content.categoryIdentifier = notificationCategory
        content.sound = .default
        content.userInfo = [candidateIdKey: infraction.id]
        if let attachment = try? UNNotificationAttachment(identifier: "photo", url: infraction.mediaURL) {
            content.attachments = [attachment]
        }
        let request = UNNotificationRequest(identifier: infraction.id, content: content, trigger: nil)
        try? await UNUserNotificationCenter.current().add(request)
        print("\(reportedMediaScannerLogTag): shown detection notification id=\(infraction.id)")
    }

    private func openForEditing(candidateId: String) async {
        guard let infraction = IOSDetectedInfractionStore.load(id: candidateId) else { return }
        try? await SharedBridge.shared.container.saveDraftUseCase.execute(draft: infraction.toDraft())
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [candidateId])
        await MainActor.run {
            NotificationCenter.default.post(name: .reportedDetectedInfractionEditRequested, object: nil)
        }
    }

    private func submit(candidateId: String) async {
        guard let infraction = IOSDetectedInfractionStore.load(id: candidateId) else { return }
        guard let session = try? await SharedBridge.shared.container.loadSessionUseCase.execute(),
              session.isAuthorized else {
            await openForEditing(candidateId: candidateId)
            await showNeedsEditNotification(candidateId: candidateId, reason: "Sign in to submit this detected report.")
            return
        }
        do {
            let mediaFiles = try await ParseMediaUploader.uploadAll([infraction.media])
            _ = try await SharedBridge.shared.container.submitReportUseCase.execute(command: SubmitReportCommand(
                plate: infraction.plate,
                plateRegion: infraction.plateRegion,
                description: "",
                notes: "",
                address: infraction.address,
                complaintIds: [infraction.complaintId],
                timeOfIncidentIso: infraction.occurredAtIso,
                latitude: infraction.latitude.map { KotlinDouble(double: $0) },
                longitude: infraction.longitude.map { KotlinDouble(double: $0) },
                vehicleImageDescription: nil,
                vehicleColor: nil,
                vehicleMake: nil,
                vehicleModel: nil,
                mediaUrls: [],
                mediaFiles: mediaFiles
            ))
            PersistentMediaStore.deleteStoredMedia([infraction.mediaURL])
            IOSDetectedInfractionStore.remove(id: candidateId)
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [candidateId])
        } catch {
            print("\(reportedMediaScannerLogTag): detected infraction submit failed \(error)")
            try? await SharedBridge.shared.container.saveDraftUseCase.execute(draft: infraction.toDraft())
            await showNeedsEditNotification(candidateId: candidateId, reason: "Open Reported to review this detected report.")
        }
    }

    private func showNeedsEditNotification(candidateId: String, reason: String) async {
        guard await IOSMediaScannerSettings.effectiveNotificationsEnabled() else {
            print("\(reportedMediaScannerLogTag): skipping needs-edit notification: notifications disabled")
            return
        }
        let content = UNMutableNotificationContent()
        content.title = "Review detected report"
        content.body = reason
        content.categoryIdentifier = notificationCategory
        content.sound = .default
        content.userInfo = [candidateIdKey: candidateId]
        try? await UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: candidateId, content: content, trigger: nil)
        )
    }

    private func beginBackgroundTask() {
        guard backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "ReportedMediaScanner") { [weak self] in
            self?.endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }

    private static func reverseGeocode(latitude: Double, longitude: Double) async -> String? {
        var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/reverse")
        components?.queryItems = [
            URLQueryItem(name: "point.lat", value: String(latitude)),
            URLQueryItem(name: "point.lon", value: String(longitude)),
            URLQueryItem(name: "size", value: "1")
        ]
        guard let url = components?.url,
              let (data, _) = try? await URLSession.shared.data(from: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = json["features"] as? [[String: Any]],
              let first = features.first,
              let properties = first["properties"] as? [String: Any] else {
            return nil
        }
        return (properties["label"] as? String)
            ?? (properties["name"] as? String)
            ?? (properties["address"] as? String)
    }
}

private func detectedInfractionId(plate: String, state: String, occurredAtIso: String) -> String {
    let raw = "\(plate)-\(state)-\(occurredAtIso)"
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
    return String(raw.unicodeScalars.map { allowed.contains($0) ? Character($0) : "-" })
}

private func scannerIsoStringUTC(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

private func notificationComplaintLabel(for complaintId: String) -> String {
    switch complaintId {
    case "Z8vjWz8uYr": return "Blocked bike lane"
    case "GzRxlMN1vl": return "Blocked crosswalk"
    default: return "Infraction"
    }
}

private let autoReportComplaintIds: Set<String> = ["Z8vjWz8uYr", "GzRxlMN1vl"]
private let autoReportStandardPlateConfidenceThreshold = 0.65
private let autoReportPostInferencePlateConfidenceThreshold = 0.65
private let autoReportStateConfidenceThreshold = 0.65
private let autoReportComplaintConfidenceThreshold = 0.65

let autoReportConfidenceThresholdSummary = "Thresholds: plate \(scannerConfidencePercent(autoReportStandardPlateConfidenceThreshold))+, state \(scannerConfidencePercent(autoReportStateConfidenceThreshold))+, infraction \(scannerConfidencePercent(autoReportComplaintConfidenceThreshold))+."

private func ifLet<A, B, T>(_ a: A?, _ b: B?, transform: (A, B) async -> T?) async -> T? {
    guard let a, let b else { return nil }
    return await transform(a, b)
}

private extension Array where Element == ComposerState.PlateCandidate {
    func bestMediaScannerCandidate() -> ComposerState.PlateCandidate? {
        filter { candidate in
            candidate.confidence >= candidate.mediaScannerPlateThreshold &&
                (candidate.stateConfidence ?? 0) >= autoReportStateConfidenceThreshold &&
                candidate.state?.isEmpty == false
        }
        .max { lhs, rhs in
            lhs.mediaScannerScore < rhs.mediaScannerScore
        }
    }

    func bestObservedMediaScannerCandidate() -> ComposerState.PlateCandidate? {
        self.max { lhs, rhs in
            lhs.mediaScannerScore < rhs.mediaScannerScore
        }
    }
}

private extension ComposerState.PlateCandidate {
    var hasPostInferredNyForHirePlate: Bool {
        state == "NY" && (plateType == "TAXI" || plateType == "TLC")
    }

    var mediaScannerPlateThreshold: Double {
        hasPostInferredNyForHirePlate ? autoReportPostInferencePlateConfidenceThreshold : autoReportStandardPlateConfidenceThreshold
    }

    var mediaScannerScore: Double {
        confidence + (stateConfidence ?? 0)
    }

    var mediaScannerConfidenceSummary: String {
        "\(plate) \(state ?? "") plate \(scannerConfidencePercent(confidence)) (needs \(scannerConfidencePercent(mediaScannerPlateThreshold))), state \(scannerConfidencePercent(stateConfidence ?? 0)) (needs \(scannerConfidencePercent(autoReportStateConfidenceThreshold)))"
    }
}

private extension Optional where Wrapped == ComplaintInferenceResult {
    var acceptedAutoReportComplaint: Bool {
        guard let result = self else { return false }
        return result.accepted && autoReportComplaintIds.contains(result.complaintId)
    }

    func autoReportComplaintConfidenceSummary(matched: Bool) -> String {
        let required = scannerConfidencePercent(autoReportComplaintConfidenceThreshold)
        guard let result = self else {
            return "Reported infraction model returned no blocked bike lane/crosswalk detection score (needs \(required))."
        }
        let label = notificationComplaintLabel(for: result.complaintId)
        let confidence = scannerConfidencePercent(Double(result.confidence))
        if matched {
            return "Reported infraction model: \(label) \(confidence) (needs \(required)), queued this photo for bulk review."
        }
        return "Reported infraction model top score: \(label) \(confidence) (needs \(required)), below Auto-Report threshold."
    }
}

private func scannerConfidencePercent(_ value: Double) -> String {
    let bounded = min(0.999, max(0, value))
    if bounded > 0, bounded < 0.01 {
        return "<1%"
    }
    return "\(Int(bounded * 100))%"
}

private extension String {
    var detectedNotificationTime: String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        guard let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self) else {
            return self
        }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
