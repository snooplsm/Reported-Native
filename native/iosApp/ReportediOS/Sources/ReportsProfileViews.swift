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

struct ReportsScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    let openReportObjectId: String?
    let onOpenedReport: () -> Void
    @StateObject private var viewModel = ReportsViewModel()
    @State private var expandedReports: Set<String> = []
    @State private var pendingDeleteReport: ReportSummary?

    var body: some View {
        if !isAuthorized {
            ScrollView {
                ScreenCard {
                    MessageView(text: "Sign in to see your history and keep track of the reports you've submitted.")
                    PrimaryButton(title: "Login", action: onRequireLogin)
                }
                .padding()
            }
        } else {
            NavigationStack {
                ReportsListContent(
                    viewModel: viewModel,
                    expandedReports: $expandedReports,
                    pendingDeleteReport: $pendingDeleteReport
                )
            }
            .onAppear {
                openSubmittedReport(openReportObjectId)
            }
            .onChange(of: openReportObjectId) { _, objectId in
                openSubmittedReport(objectId)
            }
            .confirmationDialog(
                "Delete report?",
                isPresented: Binding(
                    get: { pendingDeleteReport != nil },
                    set: { if !$0 { pendingDeleteReport = nil } }
                ),
                presenting: pendingDeleteReport
            ) { report in
                Button("Delete report", role: .destructive) {
                    viewModel.onAction(.reportDeleted(report))
                    pendingDeleteReport = nil
                }
                Button("Cancel", role: .cancel) {
                    pendingDeleteReport = nil
                }
            } message: { report in
                Text(
                    reportedLocalizedFormat(
                        "This pending report for %@ will be removed.",
                        [report.plateRegion, report.plate].filter { !$0.isEmpty }.joined(separator: " ")
                    )
                )
            }
        }
    }

    private func openSubmittedReport(_ objectId: String?) {
        guard let objectId, !objectId.isEmpty else { return }
        expandedReports.insert(objectId)
        viewModel.onAction(.reportOpened(objectId: objectId))
        onOpenedReport()
    }
}

struct ReportsListContent: View {
    @ObservedObject var viewModel: ReportsViewModel
    @Binding var expandedReports: Set<String>
    @Binding var pendingDeleteReport: ReportSummary?

    var body: some View {
        List {
            ReportsModeSection(viewModel: viewModel)
            if viewModel.mode == .search {
                ReportsSearchSection(viewModel: viewModel)
            }
            if let error = viewModel.error {
                MessageView(text: error)
            }
            ReportsResultsSection(viewModel: viewModel)
            ForEach(viewModel.reports, id: \.objectId) { report in
                ReportSummaryRow(
                    report: report,
                    detail: viewModel.reportDetails[report.objectId] ?? report,
                    isExpanded: expandedReports.contains(report.reportKey),
                    isLoadingDetail: viewModel.detailLoadingIds.contains(report.objectId),
                    isDeleting: viewModel.deletingReportKeys.contains(report.reportKey),
                    onToggle: {
                        if expandedReports.contains(report.reportKey) {
                            expandedReports.remove(report.reportKey)
                        } else {
                            expandedReports.insert(report.reportKey)
                            viewModel.onAction(.detailRequested(report))
                        }
                    },
                    onDelete: {
                        pendingDeleteReport = report
                    }
                )
                .onAppear {
                    viewModel.onAction(.nextPageRequested(current: report))
                }
            }
            if viewModel.loadingMore {
                HStack {
                    Spacer()
                    ProgressView()
                    Spacer()
                }
            }
        }
    }
}

struct ReportsModeSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        Section {
            Text("Choose how you want to pull your reports. We will not load the list until you ask.")
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                SecondaryButton(title: "Search") { viewModel.onAction(.searchChosen) }
                PrimaryButton(title: "List") { viewModel.onAction(.listChosen) }
            }
        }
    }
}

struct ReportsSearchSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        Section("Search Filters") {
            TextField(
                "License plate",
                text: Binding(
                    get: { viewModel.licenseQuery },
                    set: { viewModel.onAction(.licenseChanged($0)) }
                )
            )
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled(true)
            .textFieldStyle(.plain)
            .submitLabel(.done)
            .onSubmit { dismissActiveKeyboard() }
            .frame(height: 24)
            Toggle("Use start date", isOn: Binding(
                get: { viewModel.usesStartDate },
                set: { viewModel.onAction(.usesStartDateChanged($0)) }
            ))
            if viewModel.usesStartDate {
                DatePicker(
                    "Start Date",
                    selection: Binding(
                        get: { viewModel.startDate },
                        set: { viewModel.onAction(.startDateChanged($0)) }
                    ),
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            Toggle("Use end date", isOn: Binding(
                get: { viewModel.usesEndDate },
                set: { viewModel.onAction(.usesEndDateChanged($0)) }
            ))
            if viewModel.usesEndDate {
                DatePicker(
                    "End Date",
                    selection: Binding(
                        get: { viewModel.endDate },
                        set: { viewModel.onAction(.endDateChanged($0)) }
                    ),
                    in: ...Date(),
                    displayedComponents: [.date, .hourAndMinute]
                )
            }
            PrimaryButton(title: "Search reports") { viewModel.onAction(.searchPressed) }
        }
    }
}

struct ReportsResultsSection: View {
    @ObservedObject var viewModel: ReportsViewModel

    var body: some View {
        if viewModel.loading {
            ProgressView()
        } else if viewModel.mode != nil {
            Section("Results") {
                Text(
                    viewModel.reports.isEmpty
                        ? reportedLocalized("No reports found.")
                        : reportedLocalizedFormat(
                            viewModel.reports.count == 1 ? "%d report" : "%d reports",
                            viewModel.reports.count
                        )
                )
            }
        }
    }
}

struct ReportSummaryRow: View {
    let report: ReportSummary
    let detail: ReportSummary
    let isExpanded: Bool
    let isLoadingDetail: Bool
    let isDeleting: Bool
    let onToggle: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: onToggle) {
                reportHeader
            }
            .buttonStyle(.plain)
            if isExpanded {
                reportDetail
                if report.canDelete {
                    Button(
                        isDeleting ? reportedLocalized("Deleting...") : reportedLocalized("Delete report"),
                        role: .destructive,
                        action: onDelete
                    )
                        .disabled(isDeleting)
                }
            }
        }
        .padding(.vertical, 8)
    }

    private var reportHeader: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(report.street.isEmpty ? reportedLocalized("Unknown address") : report.street)
                        .font(.headline)
                    Text([report.plate, report.plateRegion].filter { !$0.isEmpty }.joined(separator: " - "))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text(report.status.isEmpty ? reportedLocalized("Pending") : report.status)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
            }
            if !report.incidentAt.isEmpty {
                Text(report.incidentAt.reportDateTimeDisplay).font(.caption)
            }
            if !report.complaint.isEmpty {
                Text(report.complaint).font(.subheadline)
            }
        }
    }

    private var reportDetail: some View {
        VStack(alignment: .leading, spacing: 8) {
            if isLoadingDetail {
                ProgressView()
            }
            if !detail.description_.isEmpty {
                Text(detail.description_).font(.subheadline)
            }
            if !detail.notes.isEmpty {
                Text(reportedLocalizedFormat("Notes: %@", detail.notes))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ReportMediaStrip(mediaUrls: detail.mediaUrls, videoUrls: detail.videoUrls)
            if detail.mediaUrls.isEmpty && detail.videoUrls.isEmpty && !isLoadingDetail {
                Text("No media attached.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct RemoteReportImageSelection: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

struct ReportMediaStrip: View {
    let mediaUrls: [String]
    let videoUrls: [String]
    @State private var selectedImage: RemoteReportImageSelection?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !mediaUrls.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(mediaUrls, id: \.self) { url in
                            if let imageURL = URL(string: url) {
                                Button {
                                    selectedImage = RemoteReportImageSelection(url: imageURL)
                                } label: {
                                    AsyncImage(url: imageURL) { image in
                                        image.resizable().scaledToFill()
                                    } placeholder: {
                                        Color(.secondarySystemBackground)
                                    }
                                    .frame(width: 112, height: 112)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .contentShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Open report image")
                            }
                        }
                    }
                }
            }
            ForEach(Array(videoUrls.enumerated()), id: \.offset) { index, _ in
                Text(reportedLocalizedFormat("Video %d", index + 1))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
            }
        }
        .fullScreenCover(item: $selectedImage) { selection in
            RemoteReportImageViewer(url: selection.url) {
                selectedImage = nil
            }
        }
    }
}

struct RemoteReportImageViewer: View {
    let url: URL
    let onDismiss: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .topTrailing) {
                Color.black.ignoresSafeArea()
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .tint(.white)
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        VStack(spacing: 10) {
                            Image(systemName: "photo")
                                .font(.system(size: 36, weight: .medium))
                            Text("Image unavailable")
                                .font(.headline)
                        }
                        .foregroundStyle(.white)
                    @unknown default:
                        EmptyView()
                    }
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
                    if scale > 1 {
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

extension ReportSummary {
    var reportKey: String {
        objectId.isEmpty ? "\(id)" : objectId
    }
}

extension String {
    var reportDateTimeDisplay: String {
        guard !isEmpty else { return "" }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self)
        guard let date else { return self }
        return date.formatted(
            .dateTime
                .month(.abbreviated)
                .day()
                .year()
                .hour(.defaultDigits(amPM: .abbreviated))
                .minute()
        )
    }
}

struct ProfileScreen: View {
    let isAuthorized: Bool
    let onRequireLogin: () -> Void
    var showsLogoutInToolbar = false
    @StateObject private var viewModel = ProfileViewModel()
    let onLogout: () -> Void

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = currentInterfaceIsLandscape(fallbackSize: geometry.size)
            Group {
                if !isAuthorized {
                    ScrollView {
                        ScreenCard {
                            MessageView(text: "Sign in to edit your profile and manage your account.")
                            PrimaryButton(title: "Login", action: onRequireLogin)
                        }
                        .padding(isLandscape ? 12 : 16)
                    }
                } else {
                    ScrollView {
                        ScreenCard {
                            if let error = viewModel.state.error { MessageView(text: error) }
                            profileFields(isLandscape: isLandscape)
                            Toggle("I'm willing to testify by phone if needed.", isOn: Binding(
                                get: { viewModel.state.testify },
                                set: { viewModel.onAction(.fieldsChanged(testify: $0)) }
                            ))
                            .disabled(!viewModel.state.editing)
                            if viewModel.state.editing {
                                PrimaryButton(title: viewModel.state.loading ? "Saving..." : "Save") { viewModel.onAction(.save) }
                            } else {
                                PrimaryButton(title: "Edit Profile") { viewModel.onAction(.toggleEditing) }
                            }
                            if !showsLogoutInToolbar {
                                Button("Logout", action: onLogout)
                                    .foregroundStyle(Color.reportedOrange)
                            }
                        }
                        .padding(isLandscape ? 12 : 16)
                    }
                }
            }
            .background(Color(.systemBackground))
        }
        .task {
            if isAuthorized {
                viewModel.onAction(.load)
            }
        }
    }

    @ViewBuilder
    private func profileFields(isLandscape: Bool) -> some View {
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }), disabled: !viewModel.state.editing)
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }), disabled: !viewModel.state.editing)
            }
            HStack(alignment: .top, spacing: 12) {
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }), disabled: !viewModel.state.editing)
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
            }
        } else {
            InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }), disabled: !viewModel.state.editing)
            InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), disabled: !viewModel.state.editing, keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
        }
    }
}
