package com.reported.nativeandroid.live

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.shared.model.SubmitReportCommand
import java.io.File

class SubmitLiveIncidentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_INCIDENT_ID, -1L)
        if (id <= 0L) return Result.failure()

        val store = LiveIncidentStore(applicationContext)
        val incident = store.load(id) ?: return Result.success()
        val videoUri = incident.videoUri ?: return Result.failure()

        val session = AppGraph.shared.loadSessionUseCase.execute()
        if (session?.isAuthorized != true) {
            AppGraph.shared.saveDraftUseCase.execute(incident.toDraft())
            return Result.failure()
        }

        val baseCommand = SubmitReportCommand(
            plate = incident.plate,
            plateRegion = incident.plateRegion,
            description = "",
            notes = "",
            address = incident.address,
            complaintIds = listOf(incident.complaintId),
            timeOfIncidentIso = incident.incidentAtIso,
            latitude = incident.latitude,
            longitude = incident.longitude
        )
        var attemptedCommand = baseCommand
        return runCatching {
            val media = SubmissionMedia(
                uri = videoUri,
                displayName = "live-${incident.plate}-${incident.createdAtEpochMs}.mp4",
                mimeType = "video/mp4",
                isVideo = true
            )
            val uploaded = ParseMediaUploader.upload(applicationContext, media)
            attemptedCommand = baseCommand.copy(mediaFiles = listOfNotNull(uploaded))
            AppGraph.shared.submitReportUseCase.execute(attemptedCommand)
            store.delete(incident.id)
            incident.videoUri?.deleteLocalFileUri()
            incident.thumbnailUri?.deleteLocalFileUri()
            Result.success()
        }.getOrElse { error ->
            ReportedAnalytics.logSubmitReportFailed(
                surface = "live_worker",
                stage = "background_submit",
                error = error,
                plateRegion = incident.plateRegion,
                complaintCount = if (incident.complaintId.isBlank()) 0 else 1,
                mediaCount = 1,
                hasVideo = true,
                session = session,
                command = attemptedCommand
            )
            Result.retry()
        }
    }

    companion object {
        const val KEY_INCIDENT_ID = "incident_id"
    }
}

private fun String.deleteLocalFileUri() {
    runCatching {
        val uri = Uri.parse(this)
        if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).delete()
        }
    }
}
