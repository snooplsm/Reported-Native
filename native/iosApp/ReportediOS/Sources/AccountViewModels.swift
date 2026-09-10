import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

final class ReportsViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = ReportsState()
    private var activeFilter: ReportFilter?

    var reports: [ReportSummary] { state.reports }
    var reportDetails: [String: ReportSummary] { state.reportDetails }
    var detailLoadingIds: Set<String> { state.detailLoadingIds }
    var loading: Bool { state.loading }
    var loadingMore: Bool { state.loadingMore }
    var hasMore: Bool { state.hasMore }
    var deletingReportKeys: Set<String> { state.deletingReportKeys }
    var error: String? { state.error }
    var mode: ReportsMode? { state.mode }
    var licenseQuery: String { state.licenseQuery }
    var startDate: Date { state.startDate }
    var endDate: Date { state.endDate }
    var usesStartDate: Bool { state.usesStartDate }
    var usesEndDate: Bool { state.usesEndDate }

    func onAction(_ action: ReportsAction) {
        switch action {
        case .listChosen:
            chooseList()
        case .searchChosen:
            chooseSearch()
        case .searchPressed:
            search()
        case .nextPageRequested(let report):
            loadNextPageIfNeeded(current: report)
        case .detailRequested(let report):
            loadDetail(for: report)
        case .reportOpened(let objectId):
            openReport(objectId: objectId)
        case .reportDeleted(let report):
            delete(report: report)
        case .licenseChanged(let value):
            state.licenseQuery = value
        case .startDateChanged(let value):
            state.startDate = value
        case .endDateChanged(let value):
            state.endDate = value
        case .usesStartDateChanged(let value):
            state.usesStartDate = value
        case .usesEndDateChanged(let value):
            state.usesEndDate = value
        }
    }

    func chooseList() {
        ReportedAnalytics.logReportsList()
        state.mode = .list
        state.reports = []
        state.reportDetails = [:]
        state.hasMore = false
        state.nextSkip = 0
        state.error = nil
        activeFilter = nil
        load(filter: nil, append: false)
    }

    func chooseSearch() {
        state.mode = .search
        state.reports = []
        state.reportDetails = [:]
        state.hasMore = false
        state.nextSkip = 0
        activeFilter = nil
        state.error = nil
    }

    func search() {
        let filter = ReportFilter(
            keywords: "",
            srid: "",
            complaints: [],
            whenDescription: "",
            locationDescription: "",
            license: state.licenseQuery.trimmingCharacters(in: .whitespacesAndNewlines),
            startDateIso: state.usesStartDate ? state.startDate.ISO8601Format() : "",
            endDateIso: state.usesEndDate ? state.endDate.ISO8601Format() : ""
        )
        ReportedAnalytics.logReportsSearch(
            hasLicense: !filter.license.isEmpty,
            hasStartDate: !filter.startDateIso.isEmpty,
            hasEndDate: !filter.endDateIso.isEmpty
        )
        activeFilter = filter
        load(filter: filter, append: false)
    }

    func loadNextPageIfNeeded(current report: ReportSummary) {
        guard state.hasMore, !state.loading, !state.loadingMore else { return }
        guard state.reports.suffix(5).contains(where: { reportKey($0) == reportKey(report) }) else { return }
        load(filter: activeFilter, append: true)
    }

    private func load(filter: ReportFilter?, append: Bool) {
        if append {
            state.loadingMore = true
        } else {
            state.loading = true
        }
        state.error = nil
        let skip = append ? state.nextSkip : 0
        Task {
            do {
                let page = try await SharedBridge.shared.container.fetchReportsUseCase.execute(filter: filter, skip: skip, forCurrentUser: true)
                if append {
                    let existingKeys = Set(state.reports.map(reportKey))
                    state.reports.append(contentsOf: page.reports.filter { !existingKeys.contains(reportKey($0)) })
                } else {
                    state.reports = page.reports
                    state.reportDetails = [:]
                    state.detailLoadingIds = []
                }
                state.hasMore = page.hasMore
                state.nextSkip = Int32(state.reports.count)
                state.loading = false
                state.loadingMore = false
            } catch {
                state.error = error.localizedDescription
                state.loading = false
                state.loadingMore = false
            }
        }
    }

    private func reportKey(_ report: ReportSummary) -> String {
        report.objectId.isEmpty ? "\(report.id)" : report.objectId
    }

    func loadDetail(for report: ReportSummary) {
        let objectId = report.objectId
        guard !objectId.isEmpty, state.reportDetails[objectId] == nil, !state.detailLoadingIds.contains(objectId) else { return }
        state.detailLoadingIds.insert(objectId)
        Task {
            do {
                let detail = try await SharedBridge.shared.container.fetchReportDetailUseCase.execute(objectId: objectId)
                state.reportDetails[objectId] = detail
                state.detailLoadingIds.remove(objectId)
            } catch {
                state.error = error.localizedDescription
                state.detailLoadingIds.remove(objectId)
            }
        }
    }

    func openReport(objectId: String) {
        guard !objectId.isEmpty else { return }
        state.mode = .list
        state.loading = true
        state.loadingMore = false
        state.error = nil
        state.detailLoadingIds.insert(objectId)
        Task {
            do {
                let detail = try await SharedBridge.shared.container.fetchReportDetailUseCase.execute(objectId: objectId)
                let existingKeys = Set(state.reports.map(reportKey))
                if existingKeys.contains(reportKey(detail)) {
                    state.reports = state.reports.map { reportKey($0) == reportKey(detail) ? detail : $0 }
                } else {
                    state.reports.insert(detail, at: 0)
                }
                state.reportDetails[objectId] = detail
                state.detailLoadingIds.remove(objectId)
                state.nextSkip = Int32(state.reports.count)
                state.loading = false
            } catch {
                state.error = error.localizedDescription
                state.detailLoadingIds.remove(objectId)
                state.loading = false
            }
        }
    }

    func delete(report: ReportSummary) {
        let key = reportKey(report)
        guard report.canDelete, !state.deletingReportKeys.contains(key) else { return }
        state.deletingReportKeys.insert(key)
        state.error = nil
        Task {
            do {
                try await SharedBridge.shared.container.deleteReportUseCase.execute(report: report)
                state.reports.removeAll { reportKey($0) == key }
                state.reportDetails.removeValue(forKey: report.objectId)
                state.detailLoadingIds.remove(report.objectId)
                state.deletingReportKeys.remove(key)
            } catch {
                state.error = error.localizedDescription
                state.deletingReportKeys.remove(key)
            }
        }
    }
}

@MainActor
final class ProfileViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = ProfileState()

    func onAction(_ action: ProfileAction) {
        switch action {
        case .load:
            load()
        case .toggleEditing:
            toggleEditing()
        case .save:
            save()
        case .themeModeChanged(let mode):
            setThemeMode(mode)
        case .fieldsChanged(let firstName, let lastName, let phone, let email, let testify):
            update(
                firstName: firstName,
                lastName: lastName,
                phone: phone,
                email: email,
                testify: testify
            )
        }
    }

    func load() {
        Task {
            if let themeMode = try? await SharedBridge.shared.container.loadAppThemeModeUseCase.execute() {
                state.themeMode = themeMode
            }
            if let session = try? await SharedBridge.shared.container.loadSessionUseCase.execute() {
                state.firstName = session.firstName
                state.lastName = session.lastName
                state.phone = session.phone
                state.email = session.email
                state.testify = session.testify
            }
        }
    }

    func update(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        testify: Bool? = nil
    ) {
        if let firstName { state.firstName = firstName }
        if let lastName { state.lastName = lastName }
        if let phone { state.phone = phone }
        if let email { state.email = email }
        if let testify { state.testify = testify }
    }

    func toggleEditing() {
        state.editing.toggle()
    }

    func setThemeMode(_ mode: AppThemeMode) {
        state.themeMode = mode
        Task {
            try? await SharedBridge.shared.container.saveAppThemeModeUseCase.execute(mode: mode)
        }
    }

    func save() {
        state.loading = true
        state.error = nil
        Task {
            do {
                _ = try await SharedBridge.shared.container.updateProfileUseCase.execute(
                    email: state.email,
                    phone: state.phone,
                    firstName: state.firstName,
                    lastName: state.lastName,
                    testify: state.testify
                )
                state.loading = false
                state.editing = false
            } catch {
                state.loading = false
                state.error = error.localizedDescription
            }
        }
    }
}

@MainActor
final class ThemeViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = ThemeState()

    var mode: AppThemeMode { state.mode }

    func onAction(_ action: ThemeAction) {
        switch action {
        case .load:
            load()
        case .modeChanged(let mode):
            update(mode)
        }
    }

    func load() {
        Task {
            if let loadedMode = try? await SharedBridge.shared.container.loadAppThemeModeUseCase.execute() {
                state.mode = loadedMode
            }
        }
    }

    func update(_ mode: AppThemeMode) {
        state.mode = mode
        Task {
            try? await SharedBridge.shared.container.saveAppThemeModeUseCase.execute(mode: mode)
        }
    }
}

@MainActor
