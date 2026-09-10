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

struct AutoReportGroupedPhotoCarousel: View {
    let infractions: [IOSDetectedInfraction]
    @Binding var selectedIndex: Int
    let selectedPlate: String?
    let onCandidateConfirmed: (ComposerState.PlateCandidate) -> Void
    @State private var imageViewerInfraction: IOSDetectedInfraction?

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                if let infraction = selectedInfraction {
                    AutoReportGroupedPhotoPage(
                        infraction: infraction,
                        selectedPlate: selectedPlate
                    ) {
                        imageViewerInfraction = infraction
                    }
                }
            }
            .frame(height: 190)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .gesture(photoDragGesture)

            if infractions.count > 1 {
                pageIndicator
            }
        }
        .onChange(of: infractions.map(\.id)) { _, ids in
            selectedIndex = min(selectedIndex, max(0, ids.count - 1))
        }
        .fullScreenCover(
            isPresented: Binding(
                get: { imageViewerInfraction != nil },
                set: { isPresented in
                    if !isPresented {
                        imageViewerInfraction = nil
                    }
                }
            )
        ) {
            if let infraction = imageViewerInfraction,
               let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                FullScreenImageViewer(
                    image: image,
                    candidates: infraction.composerPlateCandidates(),
                    selectedPlate: selectedPlate,
                    focalPoint: infraction.plateFocalPoint,
                    onDismiss: { imageViewerInfraction = nil },
                    onCandidateConfirmed: { candidate in
                        onCandidateConfirmed(candidate)
                        imageViewerInfraction = nil
                    }
                )
            }
        }
    }

    private var selectedInfraction: IOSDetectedInfraction? {
        guard !infractions.isEmpty else { return nil }
        return infractions[min(selectedIndex, infractions.count - 1)]
    }

    private var pageIndicator: some View {
        HStack(spacing: 10) {
            photoControlButton(systemImage: "chevron.left", isEnabled: selectedIndex > 0) {
                selectPhoto(at: selectedIndex - 1)
            }
            HStack(spacing: 7) {
                ForEach(infractions.indices, id: \.self) { index in
                    Circle()
                        .fill(index == selectedIndex ? Color.reportedOrange : Color.reportedOrange.opacity(0.32))
                        .frame(width: index == selectedIndex ? 8 : 6, height: index == selectedIndex ? 8 : 6)
                }
                Text("\(selectedIndex + 1)/\(infractions.count)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                    .padding(.leading, 2)
            }
            photoControlButton(systemImage: "chevron.right", isEnabled: selectedIndex < infractions.count - 1) {
                selectPhoto(at: selectedIndex + 1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Photo \(selectedIndex + 1) of \(infractions.count)")
    }

    private func photoControlButton(
        systemImage: String,
        isEnabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.caption.weight(.bold))
                .foregroundStyle(isEnabled ? Color.reportedOrange : Color.reportedOrange.opacity(0.28))
                .frame(width: 28, height: 28)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }

    private var photoDragGesture: some Gesture {
        DragGesture(minimumDistance: 18, coordinateSpace: .local)
            .onEnded { value in
                guard infractions.count > 1,
                      abs(value.translation.width) > abs(value.translation.height),
                      abs(value.translation.width) > 44 else {
                    return
                }
                if value.translation.width < 0 {
                    selectPhoto(at: selectedIndex + 1)
                } else {
                    selectPhoto(at: selectedIndex - 1)
                }
            }
    }

    private func selectPhoto(at index: Int) {
        guard !infractions.isEmpty else { return }
        withAnimation(.easeInOut(duration: 0.18)) {
            selectedIndex = min(max(index, 0), infractions.count - 1)
        }
    }
}

struct AutoReportPlateCropStrip: View {
    let infractions: [IOSDetectedInfraction]
    let selectedPlate: String?
    let onSelected: (IOSDetectedInfraction, ComposerState.PlateCandidate) -> Void

    var body: some View {
        if !items.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(items) { item in
                        Button {
                            onSelected(item.infraction, item.candidate)
                        } label: {
                            PlateCandidateCropPreview(
                                candidate: item.candidate,
                                sourceImage: item.sourceImage,
                                size: CGSize(width: 62, height: 34),
                                cornerRadius: 6
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Show photo \(item.index + 1)")
                    }
                }
            }
            .frame(height: 38)
        }
    }

    private var items: [AutoReportPlateCropItem] {
        infractions.enumerated().compactMap { index, infraction in
            guard let candidate = infraction.representativePlateCandidate(selectedPlate: selectedPlate) else {
                return nil
            }
            return AutoReportPlateCropItem(
                index: index,
                infraction: infraction,
                candidate: candidate,
                sourceImage: UIImage(contentsOfFile: infraction.mediaURL.path)
            )
        }
    }
}

struct AutoReportPlateCropItem: Identifiable {
    let index: Int
    let infraction: IOSDetectedInfraction
    let candidate: ComposerState.PlateCandidate
    let sourceImage: UIImage?

    var id: String {
        "\(index)-\(candidate.plate)"
    }
}

struct AutoReportGroupedPhotoPage: View {
    let infraction: IOSDetectedInfraction
    let selectedPlate: String?
    let onTap: () -> Void

    private var candidates: [ComposerState.PlateCandidate] {
        infraction.composerPlateCandidates()
    }

    var body: some View {
        ZStack {
            if let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                PlateAwareImage(
                    image: image,
                    focalPoint: infraction.plateFocalPoint
                )
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)

                StillImagePlateOverlay(
                    imageSize: image.size,
                    candidates: candidates,
                    selectedPlate: selectedPlate,
                    focalPoint: infraction.plateFocalPoint,
                    contentMode: .fill
                )
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                    .overlay {
                        VStack(spacing: 8) {
                            Image(systemName: "photo")
                                .font(.system(size: 32, weight: .medium))
                                .foregroundStyle(Color.reportedOrange)
                            Text("Image unavailable")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }
                    }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.secondarySystemBackground))
    }
}

extension IOSDetectedInfraction {
    func composerPlateCandidates(loadCropPreviews: Bool = false) -> [ComposerState.PlateCandidate] {
        let sourceImage = loadCropPreviews ? UIImage(contentsOfFile: mediaURL.path) : nil
        return candidates.map { storedCandidate in
            let candidate = storedCandidate.toComposerPlateCandidate()
            guard let sourceImage,
                  let cropPreview = sourceImage.croppedPlatePreview(for: candidate) else {
                return candidate
            }
            return storedCandidate.toComposerPlateCandidate(plateCropPreview: cropPreview)
        }
    }

    var plateFocalPoint: CGPoint? {
        let mappedCandidates = composerPlateCandidates()
        return mappedCandidates.first { $0.plate == plate }?.normalizedFocalPoint
            ?? mappedCandidates.max(by: { $0.confidence < $1.confidence })?.normalizedFocalPoint
    }

    func representativePlateCandidate(selectedPlate: String?) -> ComposerState.PlateCandidate? {
        let mappedCandidates = composerPlateCandidates(loadCropPreviews: true)
        let normalizedSelection = normalizedAutoReportPlate(selectedPlate ?? "")
        if !normalizedSelection.isEmpty,
           let selectedCandidate = mappedCandidates.first(where: {
               normalizedAutoReportPlate($0.plate) == normalizedSelection
           }) {
            return selectedCandidate
        }
        return mappedCandidates.max(by: { $0.confidence < $1.confidence })
    }
}

extension IOSDetectedInfraction.StoredCandidate {
    func toComposerPlateCandidate(plateCropPreview: UIImage? = nil) -> ComposerState.PlateCandidate {
        ComposerState.PlateCandidate(
            plate: plate,
            confidence: confidence,
            rawPlateText: rawPlateText,
            wasPlateCorrected: wasPlateCorrected ?? false,
            ownOcrText: rawPlateText,
            ownOcrConfidence: confidence,
            state: state,
            stateConfidence: stateConfidence,
            plateType: plateType,
            plateTypeLabel: plateTypeLabel,
            bounds: candidateBounds,
            cornerPoints: [],
            plateCropPreview: plateCropPreview,
            videoFramePreview: nil,
            videoFramePreviewURL: nil,
            videoFrameTimeSeconds: nil
        )
    }

    private var candidateBounds: CGRect? {
        guard let minX = boundsMinX,
              let minY = boundsMinY,
              let maxX = boundsMaxX,
              let maxY = boundsMaxY else {
            return nil
        }
        return CGRect(
            x: minX,
            y: minY,
            width: max(0, maxX - minX),
            height: max(0, maxY - minY)
        )
    }
}

struct AutoReportGroupedPhotoStrip: View {
    let infractions: [IOSDetectedInfraction]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(infractions.count) processed photo\(infractions.count == 1 ? "" : "s")")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, reportedFieldLabelHorizontalInset)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(infractions, id: \.id) { infraction in
                        AutoReportProcessedPhotoThumbnail(infraction: infraction)
                    }
                }
                .padding(.horizontal, reportedFieldLabelHorizontalInset)
            }
        }
    }
}

struct AutoReportProcessedPhotoThumbnail: View {
    let infraction: IOSDetectedInfraction

    var body: some View {
        ZStack {
            if let image = UIImage(contentsOfFile: infraction.mediaURL.path) {
                PlateAwareImage(
                    image: image,
                    focalPoint: infraction.plateFocalPoint
                )
            } else {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                    .overlay {
                        Image(systemName: "photo")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
            }
        }
        .frame(width: 84, height: 64)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

struct AutoReportReviewField: View {
    let title: String
    let value: String
    var valueFont: Font = .subheadline

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value.isEmpty ? "Not set" : value)
                .font(valueFont)
                .foregroundStyle(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

struct AutoReportSubmissionSummaryCard: View {
    let incidents: [AutoReportIncident]
    let processedPhotos: [IOSAutoReportProcessedPhoto]
    @State private var plateZoomSelection: AutoReportPlateZoomSelection?

    private var keptIncidents: [AutoReportIncident] {
        incidents.filter { $0.selected && $0.good }
    }

    private var invalidKeptIncidents: [AutoReportIncident] {
        keptIncidents.filter { !$0.isSubmittable }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Submission Summary")
                    .font(.headline)

                AutoReportReviewField(
                    title: "Reports to Submit",
                    value: "\(keptIncidents.count) report\(keptIncidents.count == 1 ? "" : "s") from \(keptIncidents.reduce(0) { $0 + $1.infractions.count }) photo\(keptIncidents.reduce(0) { $0 + $1.infractions.count } == 1 ? "" : "s")"
                )
                AutoReportReviewField(
                    title: "Processed Photos",
                    value: "\(processedPhotos.count) scanned, \(processedPhotos.filter { $0.matched }.count) matched, \(processedPhotos.filter { !$0.matched }.count) not queued"
                )
                if !invalidKeptIncidents.isEmpty {
                    AutoReportReviewField(
                        title: "Needs Edits",
                        value: "\(invalidKeptIncidents.count) kept report\(invalidKeptIncidents.count == 1 ? "" : "s") must be completed before submission."
                    )
                }

                if keptIncidents.isEmpty {
                    Text("No reports are currently marked Keep.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 10)
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(keptIncidents) { incident in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(incident.complaintTitle)
                                        .font(.subheadline.weight(.semibold))
                                    Spacer(minLength: 8)
                                    Text(incident.isSubmittable ? "Ready" : "Needs edits")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(incident.isSubmittable ? Color.reportedOrange : Color.red)
                                }
                                Text("\(incident.infractions.count) photo\(incident.infractions.count == 1 ? "" : "s") • \(incident.aiSummary)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                Text("\(incident.plate) - \(incident.plateRegion) • \(autoReportDisplayTime(incident.occurredAtIso))")
                                    .font(.subheadline.weight(.semibold))
                                AutoReportPlateCropStrip(
                                    infractions: incident.infractions,
                                    selectedPlate: incident.plate
                                ) { infraction, candidate in
                                    plateZoomSelection = AutoReportPlateZoomSelection(
                                        infraction: infraction,
                                        candidate: candidate,
                                        selectedPlate: incident.plate
                                    )
                                }
                                if !incident.address.isEmpty {
                                    Text(incident.address)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                }
            }
            .padding(12)
            .padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .fullScreenCover(item: $plateZoomSelection) { selection in
            if let image = UIImage(contentsOfFile: selection.infraction.mediaURL.path) {
                AutoReportPlateZoomViewer(
                    image: image,
                    candidates: selection.infraction.composerPlateCandidates(),
                    selectedPlate: selection.selectedPlate,
                    focalPoint: selection.candidate.normalizedFocalPoint ?? selection.infraction.plateFocalPoint
                ) {
                    plateZoomSelection = nil
                }
            }
        }
    }
}

struct AutoReportPlateZoomSelection: Identifiable {
    let id = UUID()
    let infraction: IOSDetectedInfraction
    let candidate: ComposerState.PlateCandidate
    let selectedPlate: String?
}

struct AutoReportPlateZoomViewer: View {
    let image: UIImage
    let candidates: [ComposerState.PlateCandidate]
    let selectedPlate: String?
    let focalPoint: CGPoint?
    let onDismiss: () -> Void

    @State private var scale: CGFloat = 1.35
    @State private var lastScale: CGFloat = 1.35
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()
                ZStack {
                    PlateAwareImage(
                        image: image,
                        focalPoint: focalPoint,
                        contentMode: .fill
                    )
                    StillImagePlateOverlay(
                        imageSize: image.size,
                        candidates: candidates,
                        selectedPlate: selectedPlate,
                        focalPoint: focalPoint,
                        contentMode: .fill
                    )
                }
                .frame(width: proxy.size.width, height: proxy.size.height)
                .scaleEffect(scale)
                .offset(offset)
                .contentShape(Rectangle())
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            scale = min(max(lastScale * value, 1), 6)
                            if scale <= 1.01 {
                                offset = .zero
                            }
                        }
                        .onEnded { _ in
                            scale = min(max(scale, 1), 6)
                            lastScale = scale
                            if scale <= 1.01 {
                                offset = .zero
                                lastOffset = .zero
                            }
                        }
                )
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { value in
                            guard scale > 1 else { return }
                            offset = CGSize(
                                width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in
                            lastOffset = offset
                        }
                )
                .onTapGesture(count: 2) {
                    if scale > 1.01 {
                        scale = 1
                        lastScale = 1
                        offset = .zero
                        lastOffset = .zero
                    } else {
                        scale = 2
                        lastScale = 2
                    }
                }

                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Color.black.opacity(0.62))
                        .clipShape(Circle())
                }
                .padding(16)
            }
        }
    }
}

struct AutoReportIncidentRow: View {
    @Binding var incident: AutoReportIncident

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
                if let previewURL = incident.previewURL {
                    AsyncImage(url: previewURL) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                }
            }
            .frame(width: 84, height: 64)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(incident.complaintTitle)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text("\(incident.plate) - \(incident.plateRegion)")
                    .font(.subheadline)
                    .lineLimit(1)
                Text(autoReportDisplayTime(incident.occurredAtIso))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 10) {
                    Button("Good") { incident.good = true }
                        .foregroundStyle(incident.good ? Color.reportedOrange : .secondary)
                    Button("Bad") { incident.good = false }
                        .foregroundStyle(!incident.good ? Color.reportedOrange : .secondary)
                }
                .font(.caption.weight(.semibold))
            }
            Spacer(minLength: 8)
            Toggle("Submit", isOn: $incident.selected)
                .labelsHidden()
                .tint(Color.reportedOrange)
        }
        .padding(12)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

func buildAutoReportIncidents(from matches: [IOSDetectedInfraction]) -> [AutoReportIncident] {
    let sorted = uniqueAutoReportMatches(matches).sorted {
        (autoReportDate(from: $0.occurredAtIso) ?? .distantPast) < (autoReportDate(from: $1.occurredAtIso) ?? .distantPast)
    }
    var groups: [[IOSDetectedInfraction]] = []
    for infraction in sorted {
        if let lastIndex = groups.indices.last,
           groups[lastIndex].contains(where: { shouldGroupAutoReportInfractions($0, infraction) }) {
            groups[lastIndex].append(infraction)
        } else {
            groups.append([infraction])
        }
    }
    return groups.compactMap { group in
        guard let first = group.first else { return nil }
        let best = bestAutoReportInfraction(in: group) ?? first
        return AutoReportIncident(
            id: first.id,
            plate: best.plate,
            plateRegion: best.plateRegion,
            complaintId: first.complaintId,
            occurredAtIso: group.compactMap { autoReportDate(from: $0.occurredAtIso) }.min().map(scannerIsoStringUTCForAutoReport) ?? first.occurredAtIso,
            address: group.first { !$0.address.isEmpty }?.address ?? "",
            latitude: group.compactMap(\.latitude).first,
            longitude: group.compactMap(\.longitude).first,
            infractions: group
        )
    }
}

func uniqueAutoReportMatches(_ matches: [IOSDetectedInfraction]) -> [IOSDetectedInfraction] {
    var seenHashes = Set<String>()
    var seenURLs = Set<String>()
    return matches.filter { match in
        if let contentHash = match.contentHash, !contentHash.isEmpty {
            return seenHashes.insert(contentHash).inserted
        }
        return seenURLs.insert(match.mediaURL.absoluteString).inserted
    }
}

func shouldGroupAutoReportInfractions(_ existing: IOSDetectedInfraction, _ next: IOSDetectedInfraction) -> Bool {
    guard existing.complaintId == next.complaintId,
          existing.plateRegion == next.plateRegion,
          let existingDate = autoReportDate(from: existing.occurredAtIso),
          let nextDate = autoReportDate(from: next.occurredAtIso) else {
        return false
    }
    let timeGap = abs(existingDate.timeIntervalSince(nextDate))
    let distance = autoReportPlateDistance(existing.plate, next.plate)
    let minPlateLength = min(normalizedAutoReportPlate(existing.plate).count, normalizedAutoReportPlate(next.plate).count)
    let similarPlate = distance <= (minPlateLength >= 6 ? 2 : 1)
    if similarPlate {
        let window: TimeInterval = distance == 0 ? autoReportExactPlateGroupingWindow : autoReportSimilarPlateGroupingWindow
        return timeGap <= window
    }
    guard timeGap <= autoReportLocationRescueGroupingWindow else { return false }
    return autoReportHasCloseLocation(existing, next)
}

let autoReportExactPlateGroupingWindow: TimeInterval = 5 * 60
let autoReportSimilarPlateGroupingWindow: TimeInterval = 2 * 60
let autoReportLocationRescueGroupingWindow: TimeInterval = 60
let autoReportLocationRescueDistanceMeters: CLLocationDistance = 45

func bestAutoReportInfraction(in group: [IOSDetectedInfraction]) -> IOSDetectedInfraction? {
    struct PlateCluster {
        var normalizedPlate: String
        var members: [IOSDetectedInfraction]
    }

    var clusters: [PlateCluster] = []
    for infraction in group {
        let normalized = normalizedAutoReportPlate(infraction.plate)
        guard !normalized.isEmpty else { continue }
        if let index = clusters.firstIndex(where: { cluster in
            let distance = autoReportPlateDistance(cluster.normalizedPlate, normalized)
            let minLength = min(cluster.normalizedPlate.count, normalized.count)
            return distance <= (minLength >= 6 ? 2 : 1)
        }) {
            clusters[index].members.append(infraction)
        } else {
            clusters.append(PlateCluster(normalizedPlate: normalized, members: [infraction]))
        }
    }

    let bestCluster = clusters.max { lhs, rhs in
        if lhs.members.count != rhs.members.count {
            return lhs.members.count < rhs.members.count
        }
        let lhsConfidence = lhs.members.map(\.plateConfidence).max() ?? 0
        let rhsConfidence = rhs.members.map(\.plateConfidence).max() ?? 0
        return lhsConfidence < rhsConfidence
    }
    return bestCluster?.members.max { lhs, rhs in
        lhs.plateConfidence < rhs.plateConfidence
    } ?? group.max { lhs, rhs in
        lhs.plateConfidence < rhs.plateConfidence
    }
}

func autoReportHasCloseLocation(_ lhs: IOSDetectedInfraction, _ rhs: IOSDetectedInfraction) -> Bool {
    if let lhsLocation = autoReportLocation(for: lhs),
       let rhsLocation = autoReportLocation(for: rhs),
       lhsLocation.distance(from: rhsLocation) <= autoReportLocationRescueDistanceMeters {
        return true
    }
    let lhsAddress = normalizedAutoReportAddress(lhs.address)
    let rhsAddress = normalizedAutoReportAddress(rhs.address)
    return !lhsAddress.isEmpty && lhsAddress == rhsAddress
}

func autoReportLocation(for infraction: IOSDetectedInfraction) -> CLLocation? {
    guard let latitude = infraction.latitude,
          let longitude = infraction.longitude,
          latitude.isFinite,
          longitude.isFinite,
          abs(latitude) <= 90,
          abs(longitude) <= 180,
          abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else {
        return nil
    }
    return CLLocation(latitude: latitude, longitude: longitude)
}

func normalizedAutoReportAddress(_ value: String) -> String {
    value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
}

func normalizedAutoReportPlate(_ value: String) -> String {
    value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
}

func autoReportPlateDistance(_ a: String, _ b: String) -> Int {
    let left = Array(normalizedAutoReportPlate(a))
    let right = Array(normalizedAutoReportPlate(b))
    if left == right { return 0 }
    if left.isEmpty { return right.count }
    if right.isEmpty { return left.count }

    var previous = Array(0...right.count)
    for (i, leftChar) in left.enumerated() {
        var current = Array(repeating: 0, count: right.count + 1)
        current[0] = i + 1
        for (j, rightChar) in right.enumerated() {
            let substitution = previous[j] + (leftChar == rightChar ? 0 : 1)
            current[j + 1] = min(previous[j + 1] + 1, current[j] + 1, substitution)
        }
        previous = current
    }
    return previous[right.count]
}

func autoReportComplaintTitle(for complaintId: String) -> String {
    switch complaintId {
    case "Z8vjWz8uYr": return "Blocked bike lane"
    case "GzRxlMN1vl": return "Blocked crosswalk"
    default: return "Complaint"
    }
}

func autoReportDate(from value: String) -> Date? {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let fallback = ISO8601DateFormatter()
    fallback.formatOptions = [.withInternetDateTime]
    return formatter.date(from: value) ?? fallback.date(from: value)
}

func scannerIsoStringUTCForAutoReport(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

func autoReportPercent(_ value: Double) -> String {
    let bounded = min(0.999, max(0, value))
    if bounded > 0, bounded < 0.01 {
        return "<1%"
    }
    return "\(Int(bounded * 100))%"
}

func autoReportNormalizedPlateInput(_ value: String) -> String {
    let normalized = value
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
    return String(normalized.prefix(autoReportMaxLicensePlateLength))
}

func autoReportDisplayTime(_ value: String) -> String {
    guard let date = autoReportDate(from: value) else { return value }
    return date.formatted(date: .abbreviated, time: .shortened)
}
