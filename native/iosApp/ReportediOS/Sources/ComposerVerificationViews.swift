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

extension ComposerScreen {
    @ViewBuilder
    func verifyContent(isLandscape: Bool, availableSize: CGSize) -> some View {
        let selectedCandidate = viewModel.state.plateCandidates.first { $0.plate == viewModel.state.selectedPlateCandidate }
            ?? viewModel.state.plateCandidates.first
        if isLandscape {
            HStack(alignment: .top, spacing: 14) {
                VStack(alignment: .leading, spacing: 10) {
                    if showEmbeddedLandscapeToolbar {
                        embeddedLandscapeToolbar
                    }
                    if let error = viewModel.state.error {
                        MessageView(text: error)
                    }
                    mediaPanel(
                        selectedCandidate: selectedCandidate,
                        expandPreview: true,
                        previewHeight: primaryPreviewHeight(for: availableSize)
                    )
                    landscapeMediaActions
                }
                .frame(width: landscapeMediaColumnWidth(for: availableSize.width))
                VStack(alignment: .leading, spacing: 10) {
                    verifyFormContent(isLandscape: true, isTablet: availableSize.width >= 700)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        } else {
            VStack(alignment: .leading, spacing: 10) {
                if let error = viewModel.state.error {
                    MessageView(text: error)
                }
                mediaPanel(
                    selectedCandidate: selectedCandidate,
                    expandPreview: false,
                    previewHeight: primaryPreviewHeight(for: availableSize)
                )
                addMoreMediaButton
                if viewModel.state.validationErrors.media != nil {
                    ValidationMessage(text: viewModel.state.validationErrors.media)
                }
                verifyFormContent(isLandscape: false, isTablet: availableSize.width >= 700)
            }
        }
    }

    @ViewBuilder
    func mediaPanel(
        selectedCandidate: ComposerState.PlateCandidate?,
        expandPreview: Bool,
        previewHeight: CGFloat
    ) -> some View {
        if let media = viewModel.state.primaryMedia {
            PrimarySubmissionPreview(
                mediaItems: [media] + viewModel.state.extraMedia,
                selectedCandidate: selectedCandidate,
                candidates: viewModel.state.plateCandidates,
                selectedPlate: viewModel.state.selectedPlateCandidate,
                previewHeight: previewHeight,
                onCandidateTapped: { candidate, image in
                    pendingPlatePreviewImage = image
                    pendingPlateCandidate = candidate
                },
                onCandidateConfirmedFromFullScreen: { candidate in
                    viewModel.onAction(.plateCandidateChosen(candidate))
                },
                onRemove: { media in viewModel.onAction(.mediaRemoved(media)) }
            )
            .frame(maxHeight: expandPreview ? .infinity : nil)
            .id(previewScrollId)
        }
    }

    var landscapeMediaActions: some View {
        VStack(spacing: 10) {
            addMoreMediaButton
            if viewModel.state.validationErrors.media != nil {
                ValidationMessage(text: viewModel.state.validationErrors.media)
            }
        }
    }

    var embeddedLandscapeToolbar: some View {
        ZStack {
            Text("Report")
                .font(.system(size: 20, weight: .regular))
                .lineLimit(1)
                .overlay(alignment: .trailing) {
                    Button {
                        openVoiceAssistant()
                    } label: {
                        AnimatedSparkleIcon(size: 17)
                            .frame(width: 30, height: 30)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.reportedOrange)
                    .accessibilityLabel("Reported AI")
                    .offset(x: 31)
                }
            .frame(maxWidth: .infinity, alignment: .center)

            HStack {
                Button(action: onMenuTapped) {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 34, height: 34)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Spacer(minLength: 0)
            }
        }
        .padding(.top, 6)
        .padding(.bottom, 2)
    }

    var landscapeFabRow: some View {
        HStack {
            Button(action: onClearTapped) {
                Label("Clear", systemImage: "xmark")
                    .frame(minWidth: 108)
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
            .tint(Color.reportedOrange)
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .opacity(shellHasDraftContent ? 1 : 0)
            .disabled(!shellHasDraftContent)

            Spacer()

            Button(action: submitReport) {
                Label(viewModel.state.loading ? "Submitting" : "Submit", systemImage: "paperplane.fill")
                    .frame(minWidth: 132)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
            .tint(Color.reportedOrange)
            .shadow(color: Color.black.opacity(0.16), radius: 10, x: 0, y: 4)
            .disabled(viewModel.state.loading)
        }
    }

    var addMoreMediaButton: some View {
        SecondaryButton(title: addMediaButtonTitle) {
            pendingComplaintId = viewModel.state.selectedComplaintId
            presentMediaPicker()
        }
    }

    var addMediaButtonTitle: String {
        if viewModel.state.isPhiladelphiaSubmission {
            return viewModel.state.primaryMedia == nil ? "Add photo" : "Add another photo"
        }
        return viewModel.state.primaryMedia == nil ? "Add photo or video" : "Add more photos or videos"
    }

    var submitInlineButton: some View {
        VStack(spacing: 8) {
            submitButton()
            submitProgressContent
        }
    }

    func submitBottomBar() -> some View {
        VStack(spacing: 10) {
            if viewModel.state.loading {
                submitProgressContent
            }
            submitButton()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, usesTabletSubmitMetrics ? 16 : 20)
        .padding(.top, usesTabletSubmitMetrics ? 12 : 8)
        .padding(.bottom, usesTabletSubmitMetrics ? 16 : 20)
        .background(.regularMaterial)
        .overlay(alignment: .top) {
            Divider()
        }
    }

    var horizontalUnsafeAreaWidth: CGFloat {
        let insets = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .safeAreaInsets ?? .zero
        return max(insets.left, insets.right, 0)
    }

    var usesTabletSubmitMetrics: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }

    func submitButton() -> some View {
        let buttonHeight: CGFloat = usesTabletSubmitMetrics ? 68 : 50
        let cornerRadius: CGFloat = usesTabletSubmitMetrics ? 18 : 12

        return Button(action: submitReport) {
            Text(viewModel.state.loading ? "Submitting..." : "Submit Report")
                .font(usesTabletSubmitMetrics ? .title2.weight(.semibold) : .headline.weight(.semibold))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: buttonHeight)
                .background(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(Color.reportedOrange)
                )
                .contentShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.state.loading)
        .opacity(viewModel.state.loading ? 0.72 : 1)
    }

    @ViewBuilder
    var submitProgressContent: some View {
        if viewModel.state.loading {
            VStack(spacing: 6) {
                ProgressView(value: viewModel.state.submitProgress ?? 0)
                    .progressViewStyle(.linear)
                Text(viewModel.state.submitMessage ?? "Submitting report")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    @ViewBuilder
    func verifyFormContent(isLandscape: Bool, isTablet: Bool) -> some View {
        ReportVerifyFields(
            complaintValue: complaintOptions.first(where: { $0.id == viewModel.state.selectedComplaintId })?.title ?? "Select complaint",
            plateValue: viewModel.state.plate,
            plateCandidateCount: viewModel.state.plateCandidates.count,
            plateRegionValue: viewModel.state.plateRegion,
            vehicleLookupDetails: viewModel.state.vehicleLookupDetails,
            vehicleLookupInFlight: viewModel.state.vehicleLookupInFlight,
            vehicleLookupMessage: viewModel.state.vehicleLookupMessage,
            addressValue: viewModel.state.addressQuery,
            occurredAtValue: viewModel.state.occurredAtIso.reportDateTimeDisplay,
            validationErrors: viewModel.state.validationErrors,
            descriptionText: Binding(get: { viewModel.state.description }, set: { viewModel.onAction(.fieldsChanged(description: $0)) }),
            notesText: Binding(get: { viewModel.state.notes }, set: { viewModel.onAction(.fieldsChanged(notes: $0)) }),
            philadelphiaDetails: viewModel.state.isPhiladelphiaSubmission ? viewModel.state.philadelphiaMobilityAccessDetails : nil,
            isLandscape: isLandscape,
            isTablet: isTablet,
            onComplaintTapped: {
                ReportedAnalytics.logComplaintChooserTapped(
                    surface: "verify",
                    selectedComplaintId: viewModel.state.selectedComplaintId
                )
                showComplaintChooser = true
            },
            onPlateTapped: {
                ReportedAnalytics.logPlateChooserTapped(
                    candidateCount: viewModel.state.plateCandidates.count,
                    hasPlate: !viewModel.state.plate.isEmpty
                )
                showPlateEntryScreen = true
            },
            onStateTapped: {
                ReportedAnalytics.logStateChooserTapped(currentRegion: viewModel.state.plateRegion)
                showAllPlateRegions = false
                showPlateRegionSheet = true
            },
            onAddressTapped: {
                    openAddressSearchScreen()
            },
            onAddressClear: {
                viewModel.onAction(.addressQueryChanged(""))
            },
            onAddressMapTapped: {
                openAddressMapSheet()
            },
            onOccurredAtTapped: {
            refreshOccurredAtImageTime()
            showOccurredAtPicker = true
            },
            onDescriptionTapped: {
                fullScreenTextEditorField = .description
            },
            onNotesTapped: {
                fullScreenTextEditorField = .notes
            },
            onPhiladelphiaMobilityAccessChanged: { details in
                viewModel.onAction(.philadelphiaMobilityAccessChanged(
                    blockNumber: details.blockNumber,
                    streetName: details.streetName,
                    zipCode: details.zipCode,
                    vehicleMake: details.vehicleMake,
                    vehicleModel: details.vehicleModel,
                    bodyStyle: details.bodyStyle,
                    vehicleColor: details.vehicleColor,
                    violationObserved: details.violationObserved,
                    frequency: details.frequency
                ))
            },
            onDescriptionChanged: {
                viewModel.onAction(.draftPersistenceRequested)
            },
            onNotesChanged: {
                viewModel.onAction(.draftPersistenceRequested)
            }
        )
    }

    func reportTextBinding(for field: ReportLongTextField) -> Binding<String> {
        switch field {
        case .description:
            Binding(
                get: { viewModel.state.description },
                set: { viewModel.onAction(.fieldsChanged(description: $0)) }
            )
        case .notes:
            Binding(
                get: { viewModel.state.notes },
                set: { viewModel.onAction(.fieldsChanged(notes: $0)) }
            )
        }
    }

    func openAddressSearchScreen() {
        refreshAddressImageLocation()
        showAddressSearchScreen = true
    }

    func openAddressMapSheet() {
        refreshAddressImageLocation()
        showAddressMapSheet = true
    }

    func refreshAddressImageLocation() {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else {
            refreshedPhotoAddressSuggestion = nil
            imageAddressRefreshInFlight = false
            imageAddressRefreshTask?.cancel()
            return
        }
        imageAddressRefreshTask?.cancel()
        imageAddressRefreshInFlight = true
        imageAddressRefreshTask = Task {
            let metadata = await extractSubmissionMetadata(from: media)
            let suggestion = await addressSuggestionFromImageMetadata(metadata)
            if Task.isCancelled { return }
            await MainActor.run {
                imageAddressRefreshInFlight = false
                refreshedPhotoAddressSuggestion = suggestion
            }
        }
    }

    func refreshOccurredAtImageTime() {
        guard let media = viewModel.state.primaryMedia, !media.isVideo else {
            refreshedPhotoOccurredAtIso = nil
            imageTimeRefreshInFlight = false
            imageTimeRefreshTask?.cancel()
            return
        }
        refreshedPhotoOccurredAtIso = viewModel.state.photoOccurredAtIso
        imageTimeRefreshTask?.cancel()
        imageTimeRefreshInFlight = true
        imageTimeRefreshTask = Task {
            let metadata = await extractSubmissionMetadata(from: media)
            if Task.isCancelled { return }
            await MainActor.run {
                imageTimeRefreshInFlight = false
                refreshedPhotoOccurredAtIso = metadata.occurredAtIso
                if let occurredAtIso = metadata.occurredAtIso, !occurredAtIso.isEmpty {
                    viewModel.onAction(.metadataApplied(photoOccurredAtIso: occurredAtIso))
                }
            }
        }
    }

}
