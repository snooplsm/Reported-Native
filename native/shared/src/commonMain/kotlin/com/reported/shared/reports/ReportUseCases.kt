package com.reported.shared.reports

import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportStats
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.ReportsPage
import com.reported.shared.model.SubmitReportCommand

class FetchReportsUseCase(private val repository: ReportsRepository) {
    @Throws(Exception::class)
    suspend fun execute(filter: ReportFilter?, skip: Int = 0, forCurrentUser: Boolean = true): ReportsPage =
        repository.fetchReports(filter = filter, skip = skip, forCurrentUser = forCurrentUser)
}

class FetchReportStatsUseCase(private val repository: ReportsRepository) {
    @Throws(Exception::class)
    suspend fun execute(): ReportStats = repository.fetchStats()
}

class FetchReportDetailUseCase(private val repository: ReportsRepository) {
    @Throws(Exception::class)
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

    @Throws(Exception::class)
    suspend fun execute(command: SubmitReportCommand): String {
        println("ReportedSubmit: use case started plate=${command.plate}/${command.plateRegion} media=${command.mediaFiles.size}")
        val tracker = submittedPlateRepository
        if (tracker != null) {
            val normalized = normalizedPlateKey(command.plate, command.plateRegion)
            println("ReportedSubmit: checking local duplicate cache")
            val localDuplicate = tracker.contains(command)
            println("ReportedSubmit: fetching existing reports for duplicate check")
            val existingReports = repository.fetchReports(
                filter = ReportFilter(license = command.plate),
                skip = 0,
                forCurrentUser = true
            ).reports
            println("ReportedSubmit: duplicate check returned ${existingReports.size} report(s)")
            if (existingReports.any { isDuplicateSubmission(it, command, normalized) }) {
                println("ReportedSubmit: duplicate found from server report list")
                throw DuplicateReportException(command.plate, command.plateRegion)
            }
            if (localDuplicate) {
                println("ReportedSubmit: removing stale local duplicate cache entry")
                tracker.remove(command)
            }
        }
        println("ReportedSubmit: sending Parse submission")
        val objectId = repository.submitReport(command)
        println("ReportedSubmit: Parse submission sent objectId=$objectId")
        tracker?.add(command, objectId)
        return objectId
    }
}

class PreviewVehicleEnrichmentDebugNoteUseCase(private val repository: ReportsRepository) {
    @Throws(Exception::class)
    suspend fun execute(plate: String): String? = repository.previewVehicleEnrichmentDebugNote(plate)
}

class ChangeReportStatusUseCase(private val repository: ReportsRepository) {
    @Throws(Exception::class)
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

    @Throws(Exception::class)
    suspend fun execute(reportId: Long) = repository.deleteReport(reportId)

    @Throws(Exception::class)
    suspend fun execute(report: ReportSummary) {
        if (!report.canDelete) {
            error("Only pending reports can be deleted.")
        }
        repository.deleteReport(report)
        submittedPlateRepository?.remove(report)
    }
}
