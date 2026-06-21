package com.reported.shared.reports

import com.reported.shared.api.ReportedApi
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportStats
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.ReportsPage
import com.reported.shared.model.SubmitReportCommand

class ReportsRepository(
    private val api: ReportedApi
) {
    suspend fun fetchReports(filter: ReportFilter?, skip: Int = 0, forCurrentUser: Boolean = true): ReportsPage =
        api.fetchReports(filter = filter, skip = skip, forCurrentUser = forCurrentUser)

    suspend fun fetchReportDetail(objectId: String): ReportSummary =
        api.fetchReportDetail(objectId)

    suspend fun fetchStats(): ReportStats = api.fetchReportStats()

    suspend fun submitReport(command: SubmitReportCommand): String = api.submitReport(command)

    suspend fun previewVehicleEnrichmentDebugNote(plate: String): String? =
        api.previewVehicleEnrichmentDebugNote(plate)

    suspend fun changeStatus(reportId: Long, status: String) = api.changeStatus(reportId, status)

    suspend fun deleteReport(reportId: Long) = api.deleteReport(reportId)

    suspend fun deleteReport(report: ReportSummary) {
        if (report.objectId.isNotBlank()) {
            api.deleteParseReport(report.objectId)
        } else {
            api.deleteReport(report.id)
        }
    }
}
