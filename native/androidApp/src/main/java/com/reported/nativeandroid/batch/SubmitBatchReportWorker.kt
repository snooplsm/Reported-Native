package com.reported.nativeandroid.batch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.shared.model.SubmitReportCommand

class SubmitBatchReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID) ?: return Result.failure()
        val report = BatchSubmitStore.load(applicationContext, reportId) ?: return Result.success()
        val session = AppGraph.shared.loadSessionUseCase.execute()
        val baseCommand = SubmitReportCommand(
            plate = report.plate,
            plateRegion = report.plateRegion,
            description = report.description,
            notes = report.notes,
            address = report.address,
            complaintIds = listOf(report.complaintId),
            timeOfIncidentIso = report.occurredAtIso,
            latitude = report.latitude,
            longitude = report.longitude
        )
        if (session?.isAuthorized != true) {
            ReportedAnalytics.logSubmitReportFailed(
                surface = "batch_worker",
                stage = "background_submit",
                error = IllegalStateException("Not authorized for batch submit"),
                plateRegion = report.plateRegion,
                complaintCount = if (report.complaintId.isBlank()) 0 else 1,
                mediaCount = report.media.size,
                hasVideo = report.media.any { it.isVideo },
                session = session,
                command = baseCommand
            )
            return Result.failure()
        }

        var attemptedCommand = baseCommand
        return runCatching {
            val mediaFiles = ParseMediaUploader.uploadAll(applicationContext, report.media)
            attemptedCommand = baseCommand.copy(mediaFiles = mediaFiles)
            AppGraph.shared.submitReportUseCase.execute(attemptedCommand)
            BatchSubmitStore.markSubmittedAutoReportContentHashes(applicationContext, report.submittedContentHashes)
            BatchSubmitStore.remove(applicationContext, reportId)
            Result.success()
        }.getOrElse { error ->
            ReportedAnalytics.logSubmitReportFailed(
                surface = "batch_worker",
                stage = "background_submit",
                error = error,
                plateRegion = report.plateRegion,
                complaintCount = if (report.complaintId.isBlank()) 0 else 1,
                mediaCount = report.media.size,
                hasVideo = report.media.any { it.isVideo },
                session = session,
                command = attemptedCommand
            )
            Result.retry()
        }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
    }
}
