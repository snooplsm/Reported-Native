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

struct AutoReportSummaryView: View {
    let incidents: [AutoReportIncident]

    var body: some View {
        let counts = Dictionary(grouping: incidents, by: \.complaintTitle).mapValues(\.count)
        VStack(alignment: .leading, spacing: 6) {
            Text("\(incidents.count) possible report\(incidents.count == 1 ? "" : "s")")
                .font(.headline)
            if counts.isEmpty {
                Text("Scan your recent photos to build a bulk review.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(counts.keys.sorted(), id: \.self) { title in
                    Text("\(title): \(counts[title] ?? 0)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct AutoReportProcessedPhotosView: View {
    let photos: [IOSAutoReportProcessedPhoto]

    var body: some View {
        if !photos.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Processed photos")
                    .font(.headline)
                ForEach(photos) { photo in
                    AutoReportProcessedPhotoRow(photo: photo)
                }
            }
        }
    }
}

struct AutoReportProcessedPhotoRow: View {
    let photo: IOSAutoReportProcessedPhoto

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                if let mediaURL = photo.mediaURL {
                    AsyncImage(url: mediaURL) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                }
            }
            .frame(width: 74, height: 56)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(photo.resultTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(photo.matched ? Color.reportedOrange : .primary)
                    .lineLimit(2)
                Text(photo.resultDetail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(photo.matched ? Color.reportedOrange.opacity(0.10) : Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct AutoReportScanProgressSheet: View {
    let processed: Int
    let total: Int
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Scanning Photos")
                .font(.headline)
            VStack(alignment: .leading, spacing: 8) {
                ProgressView(value: total == 0 ? 0 : Double(processed) / Double(total))
                    .tint(Color.reportedOrange)
                Text(total == 0 ? "Preparing photo scan" : "Checking photo \(processed) of \(total)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Button(role: .cancel, action: onCancel) {
                Text("Cancel")
                    .font(.body.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)
        }
        .padding(20)
    }
}

struct AutoReportReviewSheet: View {
    @Binding var incidents: [AutoReportIncident]
    let processedPhotos: [IOSAutoReportProcessedPhoto]
    let submitting: Bool
    let onSubmit: () -> Void
    @State private var selectedIndex: Int? = 0
    @State private var loggedSummarySignature: String?

    private let pageCardInset: CGFloat = 14

    private var keptIncidents: [AutoReportIncident] {
        incidents.filter { $0.selected && $0.good }
    }

    private var submitCount: Int {
        keptIncidents.count
    }

    private var invalidKeptCount: Int {
        keptIncidents.filter { !$0.isSubmittable }.count
    }

    private var pageCount: Int {
        incidents.count + 1
    }

    private var currentIndex: Int {
        min(max(selectedIndex ?? 0, 0), max(pageCount - 1, 0))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Review Auto-Reports")
                        .font(.headline)
                    Text(reviewSubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            GeometryReader { proxy in
                let pageWidth = max(proxy.size.width, 1)
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(incidents.indices, id: \.self) { index in
                            AutoReportReviewCarouselPage(width: pageWidth, cardInset: pageCardInset) {
                                AutoReportIncidentCard(incident: $incidents[index])
                            }
                            .id(index)
                        }
                        AutoReportReviewCarouselPage(width: pageWidth, cardInset: pageCardInset) {
                            AutoReportSubmissionSummaryCard(
                                incidents: incidents,
                                processedPhotos: processedPhotos
                            )
                        }
                        .id(incidents.count)
                    }
                    .scrollTargetLayout()
                }
                .scrollIndicators(.hidden)
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: $selectedIndex)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            reviewActionControls
            reviewPageIndicator
        }
        .padding(.top, 16)
        .padding(.horizontal, 16)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(.systemBackground))
        .onChange(of: incidents.count) { _, count in
            selectedIndex = min(currentIndex, count)
        }
        .onChange(of: currentIndex) { _, _ in
            logSummaryIfNeeded()
        }
        .onAppear {
            logSummaryIfNeeded()
        }
    }

    private var reviewSubtitle: String {
        let discarded = max(0, incidents.count - submitCount)
        if invalidKeptCount > 0 {
            return "\(submitCount) kept, \(discarded) discarded, \(invalidKeptCount) needs edits"
        }
        return "\(submitCount) kept, \(discarded) discarded"
    }

    private var reviewPageIndicator: some View {
        HStack(spacing: 12) {
            HStack(spacing: 7) {
                ForEach(0..<pageCount, id: \.self) { index in
                    Circle()
                        .fill(index == currentIndex ? Color.reportedOrange : Color.reportedOrange.opacity(0.28))
                        .frame(width: index == currentIndex ? 8 : 6, height: index == currentIndex ? 8 : 6)
                }
            }

            Button {
                withAnimation(.easeInOut(duration: 0.18)) {
                    selectedIndex = incidents.count
                }
            } label: {
                Text("Summary")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(currentIndex == incidents.count ? .white : Color.reportedOrange)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(currentIndex == incidents.count ? Color.reportedOrange : Color.reportedOrange.opacity(0.12))
                    )
            }
            .buttonStyle(.plain)
            .disabled(currentIndex == incidents.count)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .accessibilityLabel(reviewPageAccessibilityLabel)
    }

    private var reviewPageAccessibilityLabel: String {
        if currentIndex == incidents.count {
            return "Submission summary page"
        }
        return "Auto-report \(currentIndex + 1) of \(incidents.count)"
    }

    @ViewBuilder
    private var reviewActionControls: some View {
        if incidents.indices.contains(currentIndex) {
            let isKept = incidents[currentIndex].good && incidents[currentIndex].selected
            HStack(spacing: 10) {
                Button {
                    setCurrentIncidentKept(true)
                } label: {
                    Text("Keep")
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .tint(isKept ? Color.reportedOrange : Color(.tertiarySystemFill))

                Button {
                    setCurrentIncidentKept(false)
                } label: {
                    Text("Discard")
                        .font(.caption.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .tint(isKept ? Color.secondary : Color.red)
            }
            .padding(.horizontal, pageCardInset)
            .transition(.opacity)
        } else if currentIndex == incidents.count {
            Button {
                onSubmit()
            } label: {
                HStack {
                    if submitting {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(submitting ? "Submitting..." : "Submit \(keptIncidents.count) Report\(keptIncidents.count == 1 ? "" : "s")")
                        .font(.body.weight(.semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.reportedOrange)
            .disabled(keptIncidents.isEmpty || invalidKeptCount > 0 || submitting)
            .padding(.horizontal, pageCardInset)
        } else {
            Color.clear
                .frame(height: 42)
        }
    }

    private func logSummaryIfNeeded() {
        guard currentIndex == incidents.count, !incidents.isEmpty else { return }
        let signature = incidents.map(\.id).joined(separator: "|")
        guard loggedSummarySignature != signature else { return }
        loggedSummarySignature = signature
        ReportedAnalytics.logAutoReportSummary(
            count: incidents.count,
            keptCount: submitCount,
            discardedCount: max(0, incidents.count - submitCount),
            invalidCount: invalidKeptCount,
            mediaCount: incidents.reduce(0) { $0 + $1.media.count },
            processedPhotoCount: processedPhotos.count
        )
    }

    private func setCurrentIncidentKept(_ keep: Bool) {
        guard incidents.indices.contains(currentIndex) else { return }
        incidents[currentIndex].good = keep
        incidents[currentIndex].selected = keep
        ReportedAnalytics.logAutoReportDecision(
            keep: keep,
            reportIndex: currentIndex + 1,
            reportCount: incidents.count,
            keptCount: keptIncidents.count,
            mediaCount: incidents[currentIndex].media.count
        )
    }
}

struct AutoReportReviewCarouselPage<Content: View>: View {
    let width: CGFloat
    let cardInset: CGFloat
    @ViewBuilder let content: () -> Content
    private let verticalInset: CGFloat = 6

    var body: some View {
        let innerWidth = max(width - (cardInset * 2), 1)
        GeometryReader { proxy in
            let innerHeight = max(proxy.size.height - (verticalInset * 2), 1)
            let cardShape = RoundedRectangle(cornerRadius: 14, style: .continuous)
            content()
                .frame(width: innerWidth, height: innerHeight)
                .clipShape(cardShape)
                .overlay(
                    cardShape
                        .strokeBorder(Color.reportedOrange.opacity(0.5), lineWidth: 1.8)
                        .allowsHitTesting(false)
                )
                .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
        }
        .frame(width: width)
        .frame(maxHeight: .infinity)
        .clipped()
    }
}

struct AutoReportIncidentCard: View {
    @Binding var incident: AutoReportIncident
    @State private var showComplaintChooser = false
    @State private var showPlateEntryScreen = false
    @State private var showPlateRegionSheet = false
    @State private var showAllPlateRegions = false
    @State private var showAddressSearchScreen = false
    @State private var showAddressMapSheet = false
    @State private var showOccurredAtPicker = false
    @State private var addressQuery = ""
    @State private var addressSuggestions: [ComposerState.AddressSuggestion] = []
    @State private var addressLookupInFlight = false
    @State private var addressSearchTask: Task<Void, Never>?
    @State private var selectedPhotoIndex = 0
    @State private var fullScreenTextEditorField: ReportLongTextField?

    private var complaintOptions: [ComplaintOption] {
        complaintOptionsFor(Array(Catalogs.shared.complaintCategories))
    }

    private var validationErrors: ComposerState.ValidationErrors {
        incident.validationErrors
    }

    private var plateCandidates: [ComposerState.PlateCandidate] {
        incident.infractions.flatMap { infraction in
            infraction.composerPlateCandidates(loadCropPreviews: true)
        }
    }

    private var primaryPlateSourceImage: UIImage? {
        guard let previewURL = incident.previewURL else { return nil }
        return UIImage(contentsOfFile: previewURL.path)
    }

    private var imageAddressSuggestion: ComposerState.AddressSuggestion? {
        guard let latitude = incident.latitude,
              let longitude = incident.longitude,
              !incident.address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return ComposerState.AddressSuggestion(
            label: incident.address,
            latitude: latitude,
            longitude: longitude
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AutoReportGroupedPhotoCarousel(
                    infractions: incident.infractions,
                    selectedIndex: $selectedPhotoIndex,
                    selectedPlate: incident.plate,
                    onCandidateConfirmed: { candidate in
                        incident.plate = autoReportNormalizedPlateInput(candidate.plate)
                        if let state = candidate.state, !state.isEmpty {
                            incident.plateRegion = state
                        }
                    }
                )

                ReportVerifyFields(
                    complaintValue: complaintOptions.first(where: { $0.id == incident.complaintId })?.title ?? incident.complaintTitle,
                    plateValue: incident.plate,
                    plateCandidateCount: incident.infractions.reduce(0) { $0 + $1.candidates.count },
                    plateRegionValue: incident.plateRegion,
                    addressValue: incident.address,
                    occurredAtValue: autoReportDisplayTime(incident.occurredAtIso),
                    validationErrors: validationErrors,
                    descriptionText: $incident.description,
                    notesText: $incident.notes,
                    philadelphiaDetails: nil,
                    isLandscape: false,
                    isTablet: false,
                    usesCompactFieldRow: true,
                    onComplaintTapped: {
                        showComplaintChooser = true
                    },
                    onPlateTapped: {
                        showPlateEntryScreen = true
                    },
                    onStateTapped: {
                        showAllPlateRegions = false
                        showPlateRegionSheet = true
                    },
                    onAddressTapped: {
                        addressQuery = incident.address
                        addressSuggestions = []
                        addressLookupInFlight = false
                        showAddressSearchScreen = true
                    },
                    onAddressClear: {
                        incident.address = ""
                        incident.latitude = nil
                        incident.longitude = nil
                    },
                    onAddressMapTapped: nil,
                    onOccurredAtTapped: {
                        showOccurredAtPicker = true
                    },
                    onDescriptionTapped: {
                        fullScreenTextEditorField = .description
                    },
                    onNotesTapped: {
                        fullScreenTextEditorField = .notes
                    },
                    onPhiladelphiaMobilityAccessChanged: { _ in }
                )

                AutoReportGroupedPhotoStrip(infractions: incident.infractions)

                AutoReportReviewField(
                    title: "AI Summary",
                    value: incident.aiSummary,
                    valueFont: .callout
                )
            }
            .padding(12)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background((incident.good && incident.selected) ? Color.reportedOrange.opacity(0.06) : Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .sheet(isPresented: $showComplaintChooser) {
            ComplaintChooserSheet(
                title: "Choose Complaint",
                options: complaintOptions,
                selectedComplaintId: incident.complaintId,
                activeAnimatedComplaintId: nil
            ) { option in
                incident.complaintId = option.id
                showComplaintChooser = false
            }
            .presentationDetents([.medium, .large])
        }
        .fullScreenCover(isPresented: $showPlateEntryScreen) {
            PlateEntryScreen(
                plate: Binding(
                    get: { incident.plate },
                    set: { incident.plate = autoReportNormalizedPlateInput($0) }
                ),
                candidates: plateCandidates,
                selectedPlate: incident.plate,
                sourceImage: primaryPlateSourceImage,
                isError: validationErrors.plate != nil,
                onCancel: {
                    showPlateEntryScreen = false
                },
                onClear: {
                    incident.plate = ""
                },
                onCandidateSelected: { candidate in
                    incident.plate = autoReportNormalizedPlateInput(candidate.plate)
                    if let state = candidate.state, !state.isEmpty {
                        incident.plateRegion = state
                    }
                    showPlateEntryScreen = false
                }
            )
        }
        .fullScreenCover(item: $fullScreenTextEditorField) { field in
            FullScreenReportTextEditor(
                title: field.title,
                placeholder: field.placeholder,
                text: autoReportTextBinding(for: field),
                onDone: {
                    fullScreenTextEditorField = nil
                }
            )
        }
        .sheet(isPresented: $showPlateRegionSheet) {
            PlateRegionSheet(
                selectedValue: incident.plateRegion,
                showAllStates: $showAllPlateRegions,
                onSelected: { value in
                    incident.plateRegion = value
                    showPlateRegionSheet = false
                }
            )
            .presentationDetents([.height(showAllPlateRegions ? 300 : 176)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        }
        .fullScreenCover(isPresented: $showAddressSearchScreen) {
            AddressSearchScreen(
                query: Binding(
                    get: { addressQuery },
                    set: { value in
                        addressQuery = value
                        handleAddressQueryChanged(value)
                    }
                ),
                suggestions: addressSuggestions,
                addressProvider: reportAddressProvider(
                    latitude: incident.latitude,
                    longitude: incident.longitude,
                    address: addressQuery.isEmpty ? incident.address : addressQuery
                ),
                isLoading: addressLookupInFlight,
                isError: validationErrors.address != nil,
                imageAddressSuggestion: imageAddressSuggestion,
                hasImageAddressSource: true,
                isRefreshingImageAddress: false,
                onCancel: {
                    showAddressSearchScreen = false
                },
                onClear: {
                    addressQuery = ""
                    addressSuggestions = []
                    addressSearchTask?.cancel()
                    addressLookupInFlight = false
                },
                onOpenMap: {
                    showAddressSearchScreen = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        showAddressMapSheet = true
                    }
                },
                onSelect: selectAddress,
                onUseImageAddress: selectAddress
            )
        }
        .sheet(isPresented: $showAddressMapSheet) {
            AddressMapSheet(
                initialLatitude: incident.latitude,
                initialLongitude: incident.longitude,
                initialAddress: incident.address,
                imageAddressSuggestion: imageAddressSuggestion,
                hasImageAddressSource: true,
                isRefreshingImageAddress: false,
                onCancel: {
                    showAddressMapSheet = false
                },
                onDone: { suggestion in
                    selectAddress(suggestion)
                    showAddressMapSheet = false
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
        }
        .sheet(isPresented: $showOccurredAtPicker) {
            ReportedDateTimeSheet(
                isoValue: incident.occurredAtIso,
                photoIsoValue: incident.occurredAtIso,
                hasImageTimeSource: true,
                isRefreshingImageTime: false
            ) { iso in
                incident.occurredAtIso = iso
            }
            .presentationDetents([.height(360)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(Color(.systemBackground))
        }
    }

    private func handleAddressQueryChanged(_ newValue: String) {
        let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              trimmed != incident.address else {
            addressSearchTask?.cancel()
            addressSuggestions = []
            addressLookupInFlight = false
            return
        }
        addressSearchTask?.cancel()
        addressSearchTask = Task {
            await MainActor.run {
                addressLookupInFlight = true
            }
            try? await Task.sleep(for: .milliseconds(250))
            if Task.isCancelled { return }
            let suggestions = await searchReportAddresses(
                query: trimmed,
                latitude: incident.latitude,
                longitude: incident.longitude,
                address: incident.address
            )
            if Task.isCancelled { return }
            await MainActor.run {
                addressLookupInFlight = false
                addressSuggestions = suggestions
            }
        }
    }

    private func selectAddress(_ suggestion: ComposerState.AddressSuggestion) {
        incident.address = suggestion.label
        incident.latitude = suggestion.latitude
        incident.longitude = suggestion.longitude
        addressQuery = suggestion.label
        addressSuggestions = []
        addressLookupInFlight = false
        addressSearchTask?.cancel()
    }

    private func autoReportTextBinding(for field: ReportLongTextField) -> Binding<String> {
        switch field {
        case .description:
            Binding(get: { incident.description }, set: { incident.description = $0 })
        case .notes:
            Binding(get: { incident.notes }, set: { incident.notes = $0 })
        }
    }
}
