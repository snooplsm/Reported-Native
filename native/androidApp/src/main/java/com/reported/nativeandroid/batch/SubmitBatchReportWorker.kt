package com.reported.nativeandroid.batch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        if (session?.isAuthorized != true) return Result.failure()

        return runCatching {
            val mediaFiles = ParseMediaUploader.uploadAll(applicationContext, report.media)
            AppGraph.shared.submitReportUseCase.execute(
                SubmitReportCommand(
                    plate = report.plate,
                    plateRegion = report.plateRegion,
                    description = "",
                    notes = "",
                    address = report.address,
                    complaintIds = listOf(report.complaintId),
                    timeOfIncidentIso = report.occurredAtIso,
                    latitude = report.latitude,
                    longitude = report.longitude,
                    mediaFiles = mediaFiles
                )
            )
            BatchSubmitStore.remove(applicationContext, reportId)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val KEY_REPORT_ID = "report_id"
    }
}
