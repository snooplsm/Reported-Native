package com.reported.shared.reports

import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportStats
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.ReportsPage
import com.reported.shared.model.SubmitReportCommand

class FetchReportsUseCase(private val repository: ReportsRepository) {
    suspend fun execute(filter: ReportFilter?, skip: Int = 0, forCurrentUser: Boolean = true): ReportsPage =
        repository.fetchReports(filter = filter, skip = skip, forCurrentUser = forCurrentUser)
}

class FetchReportStatsUseCase(private val repository: ReportsRepository) {
    suspend fun execute(): ReportStats = repository.fetchStats()
}

class FetchReportDetailUseCase(private val repository: ReportsRepository) {
    suspend fun execute(objectId: String): ReportSummary = repository.fetchReportDetail(objectId)
}

class SubmitReportUseCase(private val repository: ReportsRepository) {
    constructor(
        repository: ReportsRepository,
        submittedPlateRepository: SubmittedPlateRepository
    ) : this(repository) {
        this.submittedPlateRepository = submittedPlateRepository
    }

    private var submittedPlateRepository: SubmittedPlateRepository? = null

    suspend fun execute(command: SubmitReportCommand) {
        val tracker = submittedPlateRepository
        if (tracker != null) {
            val normalized = normalizedPlateKey(command.plate, command.plateRegion)
            if (tracker.contains(command.plate, command.plateRegion)) {
                throw DuplicateReportException(command.plate, command.plateRegion)
            }
            val existingReports = repository.fetchReports(
                filter = ReportFilter(license = command.plate),
                skip = 0,
                forCurrentUser = true
            ).reports
            if (existingReports.any { normalizedPlateKey(it.plate, it.plateRegion) == normalized }) {
                tracker.add(command)
                throw DuplicateReportException(command.plate, command.plateRegion)
            }
        }
        repository.submitReport(command)
        tracker?.add(command)
    }
}

class ChangeReportStatusUseCase(private val repository: ReportsRepository) {
    suspend fun execute(reportId: Long, status: String) = repository.changeStatus(reportId, status)
}

class DeleteReportUseCase(private val repository: ReportsRepository) {
    constructor(
        repository: ReportsRepository,
        submittedPlateRepository: SubmittedPlateRepository
    ) : this(repository) {
        this.submittedPlateRepository = submittedPlateRepository
    }

    private var submittedPlateRepository: SubmittedPlateRepository? = null

    suspend fun execute(reportId: Long) = repository.deleteReport(reportId)

    suspend fun execute(report: ReportSummary) {
        if (!report.canDelete) {
            error("Only pending reports can be deleted.")
        }
        repository.deleteReport(report)
        submittedPlateRepository?.remove(report)
    }
}
