package com.reported.nativeandroid.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.nativeandroid.screens.extractSubmissionMetadata
import com.reported.nativeandroid.screens.reverseGeocodeNyc
import java.time.Instant
import kotlin.math.sqrt

class MediaScannerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!MediaScannerSettings.isEnabled(applicationContext)) return Result.success()
        if (!MediaScannerSettings.isOfflineProcessingEnabled(applicationContext)) return Result.success()
        if (!MediaScannerSettings.hasRequiredPermissions(applicationContext)) return Result.success()

        val previousScan = MediaScannerSettings.lastScanSeconds(applicationContext)
        val scanStarted = Instant.now().epochSecond
        val mediaItems = queryNewMedia(applicationContext, previousScan)
        mediaItems.forEach { media ->
            if (isStopped) return Result.retry()
            val seenKey = "${media.isVideo}:${media.uri}"
            if (MediaScannerSettings.hasSeen(applicationContext, seenKey)) return@forEach
            MediaScannerSettings.markSeen(applicationContext, seenKey)

            val candidates = if (media.isVideo) {
                emptyList()
            } else {
                NativeAlprEngine.detectLicensePlates(applicationContext, media)
            }
            val bestPlate = candidates.bestQualifiedCandidate() ?: return@forEach
            val complaintId = NativeAlprEngine.inferComplaintId(applicationContext, media) ?: return@forEach
            val metadata = extractSubmissionMetadata(applicationContext, media)
            val address = if (metadata.latitude != null && metadata.longitude != null) {
                reverseGeocodeNyc(metadata.latitude, metadata.longitude)?.label.orEmpty()
            } else {
                ""
            }
            val detected = DetectedInfraction(
                id = "${media.uri.hashCode()}-${bestPlate.plate}",
                media = media,
                plate = bestPlate.plate,
                plateRegion = bestPlate.state ?: "NY",
                plateConfidence = bestPlate.confidence,
                stateConfidence = bestPlate.stateConfidence ?: 0f,
                complaintId = complaintId,
                occurredAtIso = metadata.occurredAtIso ?: Instant.now().toString(),
                address = address,
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                candidates = candidates
            )
            DetectedInfractionStore.save(applicationContext, detected)
            DetectedInfractionNotifications.showDetected(applicationContext, detected)
        }
        MediaScannerSettings.setLastScanSeconds(applicationContext, scanStarted)
        return Result.success()
    }
}

private data class MediaStoreItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAdded: Long
)

private fun queryNewMedia(context: Context, sinceSeconds: Long): List<SubmissionMedia> {
    val images = queryMediaStore(
        context = context,
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        sinceSeconds = sinceSeconds,
        isVideo = false
    )
    val videos = queryMediaStore(
        context = context,
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        sinceSeconds = sinceSeconds,
        isVideo = true
    )
    return (images + videos)
        .sortedBy { it.dateAdded }
        .takeLast(30)
        .map {
            SubmissionMedia(
                uri = it.uri.toString(),
                displayName = it.displayName,
                mimeType = it.mimeType,
                isVideo = it.isVideo
            )
        }
}

private fun queryMediaStore(
    context: Context,
    collection: Uri,
    sinceSeconds: Long,
    isVideo: Boolean
): List<MediaStoreItem> {
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_ADDED
    )
    val selection = if (sinceSeconds > 0L) "${MediaStore.MediaColumns.DATE_ADDED} > ?" else null
    val selectionArgs = if (sinceSeconds > 0L) arrayOf(sinceSeconds.toString()) else null
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"
    return buildList {
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                add(
                    MediaStoreItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        mimeType = cursor.getString(mimeColumn).orEmpty(),
                        isVideo = isVideo,
                        dateAdded = cursor.getLong(dateColumn)
                    )
                )
            }
        }
    }
}

private fun List<PlateCandidate>.bestQualifiedCandidate(): PlateCandidate? =
    filter { candidate ->
        candidate.confidence >= 0.9f &&
            (candidate.stateConfidence ?: 0f) >= 0.9f &&
            !candidate.state.isNullOrBlank()
    }.maxByOrNull { candidate ->
        val centerX = candidate.focalPointX ?: 0.5f
        val centerY = candidate.focalPointY ?: 0.5f
        val centerPenalty = sqrt((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f))
        candidate.confidence + (candidate.stateConfidence ?: 0f) - centerPenalty
    }
