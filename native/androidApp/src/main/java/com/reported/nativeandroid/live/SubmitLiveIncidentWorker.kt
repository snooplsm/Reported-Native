package com.reported.nativeandroid.live

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.app.SubmissionMedia
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

        return runCatching {
            val media = SubmissionMedia(
                uri = videoUri,
                displayName = "live-${incident.plate}-${incident.createdAtEpochMs}.mp4",
                mimeType = "video/mp4",
                isVideo = true
            )
            val uploaded = ParseMediaUploader.upload(applicationContext, media)
            AppGraph.shared.submitReportUseCase.execute(
                SubmitReportCommand(
                    plate = incident.plate,
                    plateRegion = incident.plateRegion,
                    description = "",
                    notes = "",
                    address = incident.address,
                    complaintIds = listOf(incident.complaintId),
                    timeOfIncidentIso = incident.incidentAtIso,
                    latitude = incident.latitude,
                    longitude = incident.longitude,
                    mediaFiles = listOfNotNull(uploaded)
                )
            )
            store.delete(incident.id)
            incident.videoUri?.deleteLocalFileUri()
            incident.thumbnailUri?.deleteLocalFileUri()
            Result.success()
        }.getOrElse {
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
