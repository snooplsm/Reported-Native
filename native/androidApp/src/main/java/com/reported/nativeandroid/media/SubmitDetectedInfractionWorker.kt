package com.reported.nativeandroid.media

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.di.AppGraph
import com.reported.shared.model.SubmitReportCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
            val mediaUrl = uploadMediaToParse(applicationContext, candidate.media)
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
                    mediaUrls = listOfNotNull(mediaUrl)
                )
            )
            DetectedInfractionStore.remove(applicationContext, candidateId)
            DetectedInfractionNotifications.cancel(applicationContext, candidateId)
            Result.success()
        }.getOrElse { error ->
            AppGraph.shared.saveDraftUseCase.execute(candidate.toDraft())
            DetectedInfractionNotifications.showNeedsEdit(
                applicationContext,
                candidateId,
                error.message ?: "Open Reported to review this detected report."
            )
            Result.success()
        }
    }
}

private suspend fun uploadMediaToParse(context: Context, media: com.reported.nativeandroid.app.SubmissionMedia): String? =
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(media.uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: uri.path?.let { path -> java.io.File(path).takeIf { it.exists() }?.readBytes() }
            ?: return@withContext null
        val rawName = media.displayName.ifBlank { uri.lastPathSegment ?: if (media.isVideo) "video.mp4" else "photo.jpg" }
        val filename = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val encodedName = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        val baseUrl = BuildConfig.PARSE_SERVER_URL.trimEnd('/')
        val connection = (URL("$baseUrl/files/$encodedName").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("X-Parse-Application-Id", BuildConfig.PARSE_APPLICATION_ID)
            setRequestProperty("X-Parse-JavaScript-Key", BuildConfig.PARSE_JAVASCRIPT_KEY)
            setRequestProperty("Content-Type", media.mimeType.ifBlank { "application/octet-stream" })
        }
        try {
            connection.outputStream.use { it.write(bytes) }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (connection.responseCode !in 200..299) {
                error("Media upload failed: ${connection.responseCode} $responseText")
            }
            JSONObject(responseText).optString("url").takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }
