import AVFoundation
import CoreGraphics
import Darwin
import FirebaseAnalytics
import ImageIO
import LiteRTLM
import Lottie
import MapKit
import PhotosUI
import SharedCore
import SwiftUI
import UniformTypeIdentifiers
import UIKit
import WebKit

func reportedLocalized(_ key: String) -> String {
    NSLocalizedString(key, tableName: nil, bundle: .main, value: key, comment: "")
}

func reportedLocalizedFormat(_ key: String, _ arguments: CVarArg...) -> String {
    String(format: reportedLocalized(key), locale: Locale.current, arguments: arguments)
}

@MainActor
func dismissActiveKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

@MainActor
func currentInterfaceIsLandscape(fallbackSize: CGSize) -> Bool {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let foregroundScene = scenes.first { $0.activationState == .foregroundActive }
        ?? scenes.first { $0.activationState == .foregroundInactive }
        ?? scenes.first
    if let orientation = foregroundScene?.interfaceOrientation, orientation != .unknown {
        return orientation.isLandscape
    }
    return fallbackSize.width > fallbackSize.height
}

let preferredPlateRegions = ["NY", "NJ", "CT", "PA", "FL", "OTHER"]
let dismissedSystemNoticeKey = "reported.dismissed_system_notice"
let buyMeACoffeeURL = URL(string: "https://www.buymeacoffee.com/reported")!
let reportedRoboflowProjectURL = URL(string: "https://app.roboflow.com/reported/reported/13")!
let philadelphiaSubmissionVideoMessage = "Philadelphia Parking Authority reports do not accept videos. Add up to 2 JPG or PNG photos instead."
let reportedAiFabDiameter: CGFloat = 58
let reportedAiFabBottomPadding: CGFloat = 24
let allPlateRegions = [
    "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
    "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
    "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
    "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
    "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"
]

struct ComposerScreen: View {
    @StateObject var viewModel = ComposerViewModel()
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let showEmbeddedLandscapeToolbar: Bool
    let shellHasDraftContent: Bool
    let onMenuTapped: () -> Void
    let onClearTapped: () -> Void
    @Binding var sharedMediaImportId: String?
    @Binding var clearRequest: Int
    var voiceAssistRequest: Int = 0
    @Binding var detectedDraftOpenRequest: UUID?
    let onDraftContentChanged: (Bool) -> Void
    let onSubmitBarVisibilityChanged: (Bool) -> Void
    let onReportSubmitted: (String) -> Void
    @State var singlePickerPresented = false
    @State var multiPickerPresented = false
    @State var pickedItem: PhotosPickerItem?
    @State var pickedItems: [PhotosPickerItem] = []
    @State var pendingComplaintId: String?
    @State var metadataTask: Task<Void, Never>?
    @State var imageTimeRefreshTask: Task<Void, Never>?
    @State var refreshedPhotoOccurredAtIso: String?
    @State var imageTimeRefreshInFlight = false
    @State var imageAddressRefreshTask: Task<Void, Never>?
    @State var refreshedPhotoAddressSuggestion: ComposerState.AddressSuggestion?
    @State var imageAddressRefreshInFlight = false
    @State var videoScanTask: Task<Void, Never>?
    @State var addressSearchTask: Task<Void, Never>?
    @State var showPreferredPlateRegions = false
    @State var showAllPlateRegions = false
    @State var showPlateRegionSheet = false
    @State var showAddressMapSheet = false
    @State var showAddressSearchScreen = false
    @State var showPlateEntryScreen = false
    @State var showVoiceAssistSheet = false
    @State var voiceAssistantMeasuredHeight: CGFloat = voiceAssistantCompactSheetHeight
    @State var voiceAssistSheetDetent: PresentationDetent = .height(voiceAssistantCompactSheetHeight)
    @State var showComplaintChooser = false
    @StateObject var voiceAudio = VoiceReportAudioController()
    @State var voiceTranscript = ""
    @State var voiceDraft: VoiceReportDraft?
    @State var voiceError: String?
    @State var voiceProcessing = false
    @State var voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
    @State var voiceModelDownloading = false
    @State var voiceModelDownloadProgress: Double?
    @State var voiceImageContext: String?
    @State var voiceImageContextTask: Task<Void, Never>?
    @State var voiceProcessingTask: Task<Void, Never>?
    @State var voiceProcessingID: UUID?
    @State var pendingPlateCandidate: ComposerState.PlateCandidate?
    @State var pendingPlatePreviewImage: UIImage?
    @State var showOccurredAtPicker = false
    @State var showDiscardConfirmation = false
    @State var handledClearRequest = 0
    @State var handledVoiceAssistRequest = 0
    @State var detectionProgressMinimized = false
    @State var videoScanPaused = false
    @State var videoScanGeneration = 0
    @State var videoPreviewScrubSeconds = 0.0

    @State var showReportTutorial = false
    @State var reportTutorialChecked = false
    @State var tutorialScannerEnabled = true
    @State var tutorialNotificationsEnabled = true
    @State var isLandscapeComposer = false
    @State var isKeyboardVisible = false
    @State var fullScreenTextEditorField: ReportLongTextField?
#if DEBUG
    @State var debugMediaImportConsumed = false
#endif
    let previewScrollId = "composer-primary-media-preview"

    func complaintPickerColumns(for width: CGFloat) -> [GridItem] {
        let spacing: CGFloat = width >= 700 ? 18 : 10
        let columnCount = 2
        return Array(repeating: GridItem(.flexible(), spacing: spacing, alignment: .top), count: columnCount)
    }

    func complaintPickerMaxWidth(for width: CGFloat) -> CGFloat {
        width >= 700 ? min(width, 980) : width
    }

    func complaintTileMetrics(for width: CGFloat) -> (imageHeight: CGFloat, minHeight: CGFloat) {
        width >= 700 ? (224, 320) : (96, 150)
    }
    func primaryPreviewHeight(for availableSize: CGSize) -> CGFloat {
        availableSize.width >= 700 ? 440 : 220
    }

    func landscapeMediaColumnWidth(for width: CGFloat) -> CGFloat {
        min(max(236, width * 0.31), 276)
    }

    var complaintOptions: [ComplaintOption] {
        complaintOptionsFor(viewModel.state.complaintCategories)
    }
    var primaryPlateSourceImage: UIImage? {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else { return nil }
        return UIImage(contentsOfFile: media.fileURL.path)
    }
    var hasImageAddressSource: Bool {
        viewModel.state.primaryMedia?.isVideo == false
    }
    var selectedComplaintDetectionHint: String? {
        guard let selectedId = viewModel.state.selectedComplaintId else { return nil }
        let option = complaintOptions.first { $0.id == selectedId }
        return [option?.title, option?.id, selectedId]
            .compactMap { $0 }
            .joined(separator: " ")
    }

    var hasDraftContent: Bool {
        viewModel.state.primaryMedia != nil ||
            !viewModel.state.extraMedia.isEmpty ||
            viewModel.state.selectedComplaintId != nil ||
            !viewModel.state.plate.isEmpty ||
            !viewModel.state.addressQuery.isEmpty ||
            !viewModel.state.description.isEmpty ||
            !viewModel.state.notes.isEmpty ||
            !viewModel.state.occurredAtIso.isEmpty
    }

    var draftContentKey: String {
        [
            viewModel.state.stage == .verify ? "verify" : "pick",
            viewModel.state.primaryMedia?.fileURL.absoluteString ?? "",
            viewModel.state.extraMedia.map { $0.fileURL.absoluteString }.joined(separator: "|"),
            viewModel.state.selectedComplaintId ?? "",
            viewModel.state.plate,
            viewModel.state.plateRegion,
            viewModel.state.addressQuery,
            viewModel.state.description,
            viewModel.state.notes,
            viewModel.state.occurredAtIso
        ].joined(separator: "||")
    }

    var body: some View {
        decoratedComposerContent
    }

    var decoratedComposerContent: AnyView {
        let photoIsoValue = viewModel.state.photoOccurredAtIso ?? refreshedPhotoOccurredAtIso
        let hasImageTimeSource = viewModel.state.primaryMedia?.isVideo == false
        var view = AnyView(composerContent)
        view = AnyView(view.overlay(alignment: .bottom) { detectionProgressChip })
        view = AnyView(view.overlay(alignment: .bottomTrailing) {
            reportedAiFloatingButton
        })
        view = AnyView(view.sheet(isPresented: $showOccurredAtPicker) {
            ReportedDateTimeSheet(
                isoValue: viewModel.state.occurredAtIso,
                photoIsoValue: photoIsoValue,
                hasImageTimeSource: hasImageTimeSource,
                isRefreshingImageTime: imageTimeRefreshInFlight
            ) { iso in
                viewModel.onAction(.fieldsChanged(occurredAtIso: iso))
            }
            .presentationDetents([.height(360)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.sheet(isPresented: $showPlateRegionSheet) {
            PlateRegionSheet(
                selectedValue: viewModel.state.plateRegion,
                showAllStates: $showAllPlateRegions,
                onSelected: { value in
                    ReportedAnalytics.logStateSelected(region: value)
                    viewModel.onAction(.fieldsChanged(plateRegion: value))
                    showPlateRegionSheet = false
                }
            )
            .presentationDetents([.height(showAllPlateRegions ? 300 : 176)])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.sheet(isPresented: $showAddressMapSheet) {
            AddressMapSheet(
                initialLatitude: viewModel.state.latitude,
                initialLongitude: viewModel.state.longitude,
                initialAddress: viewModel.state.addressQuery.isEmpty ? viewModel.state.address : viewModel.state.addressQuery,
                imageAddressSuggestion: refreshedPhotoAddressSuggestion,
                hasImageAddressSource: hasImageAddressSource,
                isRefreshingImageAddress: imageAddressRefreshInFlight,
                onCancel: { showAddressMapSheet = false },
                onDone: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressMapSheet = false
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        })
        view = AnyView(view.fullScreenCover(isPresented: $showAddressSearchScreen) {
            AddressSearchScreen(
                query: Binding(
                    get: { viewModel.state.addressQuery },
                    set: { viewModel.onAction(.addressQueryChanged($0)) }
                ),
                suggestions: viewModel.state.addressSuggestions,
                addressProvider: reportAddressProvider(
                    latitude: viewModel.state.latitude,
                    longitude: viewModel.state.longitude,
                    address: viewModel.state.addressQuery.isEmpty ? viewModel.state.address : viewModel.state.addressQuery
                ),
                isLoading: viewModel.state.lookupInFlight,
                isError: viewModel.state.validationErrors.address != nil,
                imageAddressSuggestion: refreshedPhotoAddressSuggestion,
                hasImageAddressSource: hasImageAddressSource,
                isRefreshingImageAddress: imageAddressRefreshInFlight,
                onCancel: {
                    showAddressSearchScreen = false
                },
                onClear: {
                    viewModel.onAction(.addressQueryChanged(""))
                },
                onOpenMap: {
                    showAddressSearchScreen = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        openAddressMapSheet()
                    }
                },
                onSelect: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressSearchScreen = false
                },
                onUseImageAddress: { suggestion in
                    viewModel.onAction(.addressChosen(suggestion))
                    showAddressSearchScreen = false
                }
            )
        })
        view = AnyView(view.fullScreenCover(isPresented: $showPlateEntryScreen) {
            PlateEntryScreen(
                plate: Binding(
                    get: { viewModel.state.plate },
                    set: { viewModel.onAction(.fieldsChanged(plate: $0)) }
                ),
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                sourceImage: primaryPlateSourceImage,
                isError: viewModel.state.validationErrors.plate != nil,
                onCancel: {
                    showPlateEntryScreen = false
                },
                onClear: {
                    viewModel.onAction(.fieldsChanged(plate: ""))
                },
                onCandidateSelected: { candidate in
                    viewModel.onAction(.plateCandidateChosen(candidate))
                    showPlateEntryScreen = false
                }
            )
        })
        view = AnyView(view.fullScreenCover(item: $fullScreenTextEditorField) { field in
            FullScreenReportTextEditor(
                title: field.title,
                placeholder: field.placeholder,
                text: reportTextBinding(for: field),
                onDone: {
                    fullScreenTextEditorField = nil
                    viewModel.onAction(.draftPersistenceRequested)
                }
            )
        })
        view = AnyView(view.sheet(isPresented: $showReportTutorial) {
            NewReportTutorialSheet(
                scannerAvailable: RemoteConfigOverrides.shared.enableMediaScanner,
                scannerEnabled: $tutorialScannerEnabled,
                notificationsEnabled: $tutorialNotificationsEnabled,
                onSkip: {
                    ReportTutorialSettings.markSeen()
                    showReportTutorial = false
                },
                onComplete: {
                    Task { await completeReportTutorialFromButton() }
                }
            )
            .presentationDetents([.fraction(0.72)])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled()
        })
        view = AnyView(view.safeAreaInset(edge: .bottom, spacing: 0) {
            if showsBottomSubmitBar {
                submitBottomBar()
            }
        })
        view = AnyView(view.overlay(alignment: .bottom) {
            voiceAssistantFloatingOverlay
        })
        view = AnyView(view.onAppear {
            onSubmitBarVisibilityChanged(showsBottomSubmitBar)
        })
        view = AnyView(view.onDisappear {
            onSubmitBarVisibilityChanged(false)
        })
        view = AnyView(view.onChange(of: showsBottomSubmitBar) { _, isVisible in
            onSubmitBarVisibilityChanged(isVisible)
        })
        view = AnyView(view.onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
            isKeyboardVisible = true
        })
        view = AnyView(view.onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
            isKeyboardVisible = false
        })
        view = AnyView(view.overlay { detectionProgressModal })
        view = AnyView(view.alert("Clear report?", isPresented: $showDiscardConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {
                viewModel.onAction(.clearDraft)
                onDraftContentChanged(false)
            }
        } message: {
            Text("This will discard the current report draft.")
        })
        view = AnyView(view.onChange(of: viewModel.state.detectingPlates) { _, _ in
            detectionProgressMinimized = false
            if !viewModel.state.detectingPlates {
                videoScanPaused = false
            }
        })
        view = AnyView(view.onChange(of: viewModel.state.detectionFrameTimeSeconds) { _, newValue in
            if !videoScanPaused {
                videoPreviewScrubSeconds = newValue
            }
        })
        view = AnyView(view.onChange(of: viewModel.state.addressQuery) { oldValue, newValue in
            handleAddressQueryChanged(oldValue, newValue)
        })
        view = AnyView(view.onAppear {
            onDraftContentChanged(hasDraftContent)
            if !reportTutorialChecked {
                reportTutorialChecked = true
                if !ReportTutorialSettings.hasSeen {
                    tutorialScannerEnabled = true
                    tutorialNotificationsEnabled = true
                    showReportTutorial = true
                }
            }
        })
        view = AnyView(view.onChange(of: draftContentKey) { _, _ in
            onDraftContentChanged(hasDraftContent)
        })
        view = AnyView(view.onChange(of: viewModel.submittedReportObjectId) { _, objectId in
            guard let objectId, !objectId.isEmpty else { return }
            onReportSubmitted(objectId)
            viewModel.onAction(.submittedReportConsumed)
        })
        view = AnyView(view.onChange(of: viewModel.state.primaryMedia?.fileURL.path) { _, _ in
            imageTimeRefreshTask?.cancel()
            refreshedPhotoOccurredAtIso = nil
            imageTimeRefreshInFlight = false
            imageAddressRefreshTask?.cancel()
            refreshedPhotoAddressSuggestion = nil
            imageAddressRefreshInFlight = false
        })
        view = AnyView(view.onChange(of: clearRequest) { _, token in
            guard token != handledClearRequest else { return }
            handledClearRequest = token
            if hasDraftContent {
                showDiscardConfirmation = true
            }
        })
        view = AnyView(view.onChange(of: voiceAssistRequest) { _, token in
            guard token != handledVoiceAssistRequest else { return }
            handledVoiceAssistRequest = token
            openVoiceAssistant()
        })
        view = AnyView(view.modifier(ComposerPickerModifier(
            singlePickerPresented: $singlePickerPresented,
            multiPickerPresented: $multiPickerPresented,
            pickedItem: $pickedItem,
            pickedItems: $pickedItems,
            maxSelectionCount: max(1, viewModel.remainingMediaSlots),
            allowsVideos: !viewModel.state.isPhiladelphiaSubmission,
            handlePickedItem: handlePickedItem,
            handlePickedItems: handlePickedItems
        )))
        view = AnyView(view.task(id: "\(sharedMediaImportId ?? "")-\(viewModel.state.draftLoaded)") {
            guard let importId = sharedMediaImportId else { return }
            guard viewModel.state.draftLoaded else { return }
            await handleSharedMediaImport(id: importId)
            sharedMediaImportId = nil
        })
#if DEBUG
        view = AnyView(view.task(id: viewModel.state.draftLoaded) {
            await handleDebugMediaImportIfNeeded()
        })
#endif
        view = AnyView(view.onChange(of: detectedDraftOpenRequest) { _, request in
            guard request != nil else { return }
            viewModel.onAction(.reloadDraft)
            detectedDraftOpenRequest = nil
        })
        view = AnyView(view.sheet(
            isPresented: Binding(
                get: { viewModel.state.complaintSheetOpen },
                set: { _ in }
            )
        ) {
            pendingComplaintSheet
        })
        view = AnyView(view.alert("Process video for license plates?", isPresented: Binding(
            get: { viewModel.state.awaitingVideoProcessingDecision },
            set: { _ in }
        )) {
            Button("Process") { startVideoScan() }
            Button("Skip", role: .cancel) {
                viewModel.onAction(.videoProcessingDecision(false))
            }
        } message: {
            Text("We can scan every video frame and show the best candidate plates for you to choose from.")
        })
        view = AnyView(view.sheet(isPresented: $showComplaintChooser) {
            ComplaintChooserSheet(
                title: "What kind of complaint is this?",
                options: complaintOptions,
                selectedComplaintId: viewModel.state.selectedComplaintId
            ) { option in
                ReportedAnalytics.logComplaintSelected(complaintId: option.id, surface: "verify")
                viewModel.onAction(.selectedComplaintChanged(option.id))
                showComplaintChooser = false
            }
            .presentationDetents([.height(complaintChooserSheetDetentHeight(optionCount: complaintOptions.count))])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.sheet(item: $pendingPlateCandidate) { candidate in
            UsePlateCandidateSheet(
                candidate: candidate,
                sourceImage: pendingPlatePreviewImage,
                onUse: {
                    viewModel.onAction(.plateCandidateChosen(candidate))
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                },
                onDismiss: {
                    pendingPlateCandidate = nil
                    pendingPlatePreviewImage = nil
                }
            )
            .presentationDetents([.height(plateCandidateSheetDetentHeight)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        })
        view = AnyView(view.alert(
            "Fix plate format?",
            isPresented: Binding(
                get: { viewModel.state.plateCorrectionPrompt != nil },
                set: { if !$0 { viewModel.onAction(.plateCorrectionDismissed) } }
            ),
            presenting: viewModel.state.plateCorrectionPrompt
        ) { prompt in
            Button("Use \(prompt.suggestedPlate)") {
                viewModel.onAction(.plateCorrectionAccepted)
            }
            Button("Keep") {
                viewModel.onAction(.plateCorrectionKept)
            }
            Button("Edit", role: .cancel) {
                viewModel.onAction(.plateCorrectionDismissed)
            }
        } message: { prompt in
            Text("This looks like a \(prompt.label) plate. Use \(prompt.suggestedPlate) instead of \(prompt.rawPlate)?")
        })
        return view
    }

    func submitReport() {
        ReportedAnalytics.logSubmitReportTapped(
            stage: viewModel.state.stage == .verify ? "verify" : "pick_media",
            isAuthorized: isAuthorized,
            mediaToSubmitMillis: viewModel.state.mediaToSubmitMillis
        )
        guard viewModel.onAction(.submitValidationRequested) else { return }
        if isAuthorized {
            viewModel.onAction(.submitPressed)
        } else {
            onRequireLogin()
        }
    }

    func presentMediaPicker() {
        guard viewModel.remainingMediaSlots > 0 else {
            viewModel.onAction(.mediaLimitReached)
            return
        }
        multiPickerPresented = true
    }

    func handlePickedItem(_ item: PhotosPickerItem) async {
        await handlePickedItems([item])
    }

    func handlePickedItems(_ items: [PhotosPickerItem]) async {
        guard !items.isEmpty else { return }
        var mediaItems: [ComposerState.SubmissionMedia] = []
        for item in items {
            if let media = await loadSubmissionMedia(from: item) {
                mediaItems.append(media)
            }
        }
        let complaintId = pendingComplaintId
        pendingComplaintId = nil
        await applyPickedMedia(mediaItems, preferredComplaintId: complaintId)
    }

    func applyPickedMedia(_ mediaItems: [ComposerState.SubmissionMedia], preferredComplaintId: String?) async {
        guard let primary = mediaItems.first else { return }

        if viewModel.state.primaryMedia != nil {
            for media in mediaItems {
                guard viewModel.onAction(.extraMediaAdded(media)) else { break }
                await inspectExtraMediaForPhiladelphiaVideo(media)
            }
            return
        }

        if let complaintId = preferredComplaintId {
            viewModel.onAction(.primaryMediaChosen(primary, complaintId: complaintId))
        } else {
            viewModel.onAction(.uploadMediaChosen(primary))
        }
        for media in mediaItems.dropFirst() {
            guard viewModel.onAction(.extraMediaAdded(media)) else { break }
            await inspectExtraMediaForPhiladelphiaVideo(media)
        }
        await processMetadataAndDetection(for: primary)
    }

    func inspectExtraMediaForPhiladelphiaVideo(_ media: ComposerState.SubmissionMedia) async {
        guard media.isVideo else { return }
        let metadata = await extractSubmissionMetadata(from: media)
        guard let latitude = metadata.latitude,
              let longitude = metadata.longitude,
              reportAddressProvider(latitude: latitude, longitude: longitude, address: "") == .philadelphia else {
            return
        }
        await MainActor.run {
            _ = viewModel.onAction(.mediaRejected(media, message: philadelphiaSubmissionVideoMessage))
        }
    }

    func handleSharedMediaImport(id: String) async {
        let mediaItems = SharedMediaImportStore.consumeImport(id: id)
        guard !mediaItems.isEmpty else { return }
        await applyPickedMedia(mediaItems, preferredComplaintId: viewModel.state.selectedComplaintId)
    }

#if DEBUG
    func handleDebugMediaImportIfNeeded() async {
        guard !debugMediaImportConsumed else { return }
        guard viewModel.state.draftLoaded else { return }
        let sourcePaths = debugMediaImportPaths()
        guard !sourcePaths.isEmpty else { return }

        debugMediaImportConsumed = true
        let mediaItems = sourcePaths.compactMap { sourcePath -> ComposerState.SubmissionMedia? in
            let sourceURL = URL(fileURLWithPath: sourcePath)
            guard FileManager.default.fileExists(atPath: sourceURL.path) else {
                print("ReportedDebugImport: missing media at \(sourceURL.path)")
                return nil
            }

            do {
                let data = try Data(contentsOf: sourceURL)
                let fileURL = try PersistentMediaStore.save(
                    data: data,
                    preferredName: sourceURL.deletingPathExtension().lastPathComponent,
                    fileExtension: sourceURL.pathExtension.isEmpty ? "jpg" : sourceURL.pathExtension
                )
                print("ReportedDebugImport: importing \(fileURL.path)")
                return ComposerState.SubmissionMedia(
                    fileURL: fileURL,
                    displayName: fileURL.lastPathComponent,
                    isVideo: false
                )
            } catch {
                print("ReportedDebugImport: failed to import \(sourceURL.path): \(error)")
                return nil
            }
        }
        guard let primary = mediaItems.first else { return }
        viewModel.onAction(.uploadMediaChosen(primary))
        mediaItems.dropFirst().forEach { viewModel.onAction(.extraMediaAdded($0)) }
        await processMetadataAndDetection(for: primary)
    }

    func debugMediaImportPaths() -> [String] {
        let arguments = ProcessInfo.processInfo.arguments
        var paths: [String] = []
        var index = arguments.startIndex
        while index < arguments.endIndex {
            if arguments[index] == "--reported-debug-import-media" {
                let nextIndex = arguments.index(after: index)
                if nextIndex < arguments.endIndex {
                    paths.append(arguments[nextIndex])
                    index = nextIndex
                }
            }
            index = arguments.index(after: index)
        }
        if let environmentValue = ProcessInfo.processInfo.environment["REPORTED_DEBUG_IMPORT_MEDIA"] {
            paths.append(contentsOf: environmentValue.split(separator: "|").map(String.init))
        }
        return paths.filter { !$0.isEmpty }
    }
#endif

    func processMetadataAndDetection(for media: ComposerState.SubmissionMedia) async {
        metadataTask?.cancel()
        metadataTask = Task {
            viewModel.onAction(.detectionProgressChanged(message: "Reading media metadata", progress: 0.1))
            let metadata = await extractSubmissionMetadata(from: media)
            let addressSuggestion: ComposerState.AddressSuggestion?
            if let latitude = metadata.latitude, let longitude = metadata.longitude {
                let provider = reportAddressProvider(latitude: latitude, longitude: longitude, address: "")
                viewModel.onAction(.detectionProgressChanged(message: provider.reverseLookupLabel, progress: 0.35))
                addressSuggestion = await reverseGeocodeAddress(latitude: latitude, longitude: longitude)
            } else {
                addressSuggestion = nil
            }
            if Task.isCancelled { return }
            if media.isVideo,
               let latitude = metadata.latitude,
               let longitude = metadata.longitude,
               reportAddressProvider(latitude: latitude, longitude: longitude, address: "") == .philadelphia {
                viewModel.onAction(.mediaRejected(media, message: philadelphiaSubmissionVideoMessage))
                return
            }
            viewModel.onAction(.metadataApplied(
                occurredAtIso: metadata.occurredAtIso,
                photoOccurredAtIso: media.isVideo ? nil : metadata.occurredAtIso,
                latitude: metadata.latitude,
                longitude: metadata.longitude,
                inferredState: addressSuggestion?.region,
                inferredAddress: addressSuggestion?.label,
                addressSuggestion: addressSuggestion
            ))
            if !media.isVideo {
                viewModel.onAction(.detectionProgressChanged(message: "Detecting plates", progress: 0.65))
                let candidates = await NativeAlprEngine.shared.detectLicensePlates(
                    media: media,
                    expectedComplaintHint: selectedComplaintDetectionHint
                )
#if DEBUG
                let summary = candidates.map { candidate in
                    "\(candidate.plate)/\(candidate.state ?? "-") conf=\(candidate.confidence) raw=\(candidate.rawPlateText ?? "-")"
                }.joined(separator: ", ")
                print("ReportedALPR: candidates=\(candidates.count) [\(summary)]")
#endif
                if !Task.isCancelled {
                    viewModel.onAction(.detectionFinished(
                        candidates: candidates,
                        inferredPlate: candidates.first?.plate,
                        inferredState: addressSuggestion?.region
                    ))
                }
            }
        }
    }

}

struct FlowLayout<Content: View>: View {
    let spacing: CGFloat
    @ViewBuilder let content: Content

    init(spacing: CGFloat = 8, @ViewBuilder content: () -> Content) {
        self.spacing = spacing
        self.content = content()
    }

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 52), spacing: spacing)], spacing: spacing) {
            content
        }
    }
}

struct ProviderSignInButton: View {
    let title: String
    let systemImage: String?
    let assetImage: String?
    let foregroundColor: Color
    let backgroundColor: Color
    let borderColor: Color
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.system(size: 22))
                        .frame(width: 24, height: 24)
                } else if let assetImage {
                    Image(assetImage)
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .frame(width: 24, height: 24)
                }
                Text(reportedLocalized(title))
                    .font(.headline)
                    .frame(maxWidth: .infinity)
            }
            .foregroundStyle(foregroundColor)
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .opacity(enabled ? 1 : 0.55)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct AuthDivider: View {
    var body: some View {
        HStack(spacing: 14) {
            Rectangle()
                .fill(Color(.separator))
                .frame(height: 1)
            Text("or")
                .font(.callout)
                .foregroundStyle(.secondary)
            Rectangle()
                .fill(Color(.separator))
                .frame(height: 1)
        }
        .padding(.vertical, 2)
    }
}

enum ReportedButtonSize {
    case regular
    case compact

    var font: Font {
        switch self {
        case .regular:
            return .body.weight(.semibold)
        case .compact:
            return .callout.weight(.semibold)
        }
    }

    var verticalPadding: CGFloat {
        switch self {
        case .regular:
            return 14
        case .compact:
            return 10
        }
    }
}

struct PrimaryButton: View {
    let title: String
    var size: ReportedButtonSize = .regular
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(reportedLocalized(title))
                .font(size.font)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, size.verticalPadding)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.reportedOrange)
    }
}

struct SecondaryButton: View {
    let title: String
    var size: ReportedButtonSize = .regular
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(reportedLocalized(title))
                .font(size.font)
                .foregroundStyle(Color.reportedOrange)
                .frame(maxWidth: .infinity)
                .padding(.vertical, size.verticalPadding)
        }
        .buttonStyle(.bordered)
        .tint(Color.reportedOrange)
    }
}

struct TertiaryButton: View {
    let title: String
    var foregroundColor: Color = .primary.opacity(0.88)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(reportedLocalized(title))
                .foregroundStyle(foregroundColor)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

struct ThemeChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(reportedLocalized(title))
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? .white : .primary)
        .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.reportedOrange : Color(.separator), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

func appPreferredColorScheme(for mode: AppThemeMode) -> ColorScheme? {
    switch mode {
    case .light:
        return .light
    case .dark:
        return .dark
    default:
        return nil
    }
}

struct ComplaintChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(reportedLocalized(title))
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? .white : .primary)
        .background(isSelected ? Color.reportedOrange : Color(.secondarySystemBackground))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.reportedOrange : Color(.separator), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

extension Color {
    static let reportedOrange = Color(red: 236 / 255, green: 104 / 255, blue: 44 / 255)
    static let reportedFieldErrorBackground = Color(red: 1, green: 241 / 255, blue: 241 / 255)
}
