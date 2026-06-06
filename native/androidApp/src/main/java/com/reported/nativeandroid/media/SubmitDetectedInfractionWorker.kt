package com.reported.nativeandroid.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.di.AppGraph
import com.reported.shared.model.SubmitReportCommand

class SubmitDetectedInfractionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val candidateId = inputData.getString(DetectedInfractionNotifications.EXTRA_CANDIDATE_ID)
            ?: return Result.failure()
        val candidate = DetectedInfractionStore.load(applicationContext, candidateId)
            ?: return Result.failure()

        val session = AppGraph.shared.loadSessionUseCase.execute()
        if (session?.isAuthorized != true) {
            AppGraph.shared.saveDraftUseCase.execute(candidate.toDraft())
            DetectedInfractionNotifications.showNeedsEdit(
                applicationContext,
                candidateId,
                "Sign in to submit this detected report."
            )
            return Result.success()
        }

        return runCatching {
            val mediaFile = ParseMediaUploader.upload(applicationContext, candidate.media)
            AppGraph.shared.submitReportUseCase.execute(
                SubmitReportCommand(
                    plate = candidate.plate,
                    plateRegion = candidate.plateRegion,
                    description = "",
                    notes = "",
                    address = candidate.address,
                    complaintIds = listOf(candidate.complaintId),
                    timeOfIncidentIso = candidate.occurredAtIso,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    mediaFiles = listOfNotNull(mediaFile)
                )
            )
            LocalSubmissionMediaCleaner.cleanupAfterSuccessfulSubmit(
                context = applicationContext,
                media = listOf(candidate.media),
                plateCandidates = candidate.candidates
            )
            DetectedInfractionStore.remove(applicationContext, candidateId)
            DetectedInfractionNotifications.cancel(applicationContext, candidateId)
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Detected infraction submit failed", error)
            AppGraph.shared.saveDraftUseCase.execute(candidate.toDraft())
            DetectedInfractionNotifications.showNeedsEdit(
                applicationContext,
                candidateId,
                "Open Reported to review this detected report."
            )
            Result.success()
        }
    }
}

private const val TAG = "ReportedMediaScanner"
