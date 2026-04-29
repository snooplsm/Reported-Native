package com.reported.nativeandroid.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.nativeandroid.screens.extractSubmissionMetadata
import com.reported.nativeandroid.screens.reverseGeocodeAddress
import java.time.Instant
import java.util.Locale
import kotlin.math.sqrt

class MediaScannerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "Media scan started")
        if (!MediaScannerSettings.isEnabled(applicationContext)) {
            Log.d(TAG, "Skipping scan: media scanner is disabled")
            return Result.success()
        }
        if (!MediaScannerSettings.isOfflineProcessingEnabled(applicationContext)) {
            Log.d(TAG, "Skipping scan: offline processing is disabled")
            return Result.success()
        }
        if (!MediaScannerSettings.supportsBackgroundLibraryScanning()) {
            Log.d(TAG, "Skipping scan: background library scanning is disabled for Play release builds")
            return Result.success()
        }
        if (!MediaScannerSettings.hasRequiredPermissions(applicationContext)) {
            Log.d(TAG, "Skipping scan: required media permissions are missing")
            return Result.success()
        }

        val storedPreviousScan = MediaScannerSettings.lastScanSeconds(applicationContext)
        val previousScan = if (BuildConfig.DEBUG) {
            (Instant.now().epochSecond - DEBUG_SCAN_LOOKBACK_SECONDS).coerceAtLeast(0L)
        } else {
            storedPreviousScan
        }
        val scanStarted = Instant.now().epochSecond
        val mediaItems = queryNewMedia(applicationContext, previousScan)
        Log.d(
            TAG,
            "Found ${mediaItems.size} media item(s) since $previousScan" +
                if (BuildConfig.DEBUG) " (debug reprocess enabled; storedLastScan=$storedPreviousScan)" else ""
        )
        mediaItems.forEach { media ->
            if (isStopped) {
                Log.d(TAG, "Scan stopped; retrying later")
                return Result.retry()
            }
            val seenKey = "${media.isVideo}:${media.uri}"
            if (!BuildConfig.DEBUG && MediaScannerSettings.hasSeen(applicationContext, seenKey)) {
                Log.d(TAG, "Skipping already-seen media: ${media.uri}")
                return@forEach
            }
            if (BuildConfig.DEBUG && MediaScannerSettings.hasSeen(applicationContext, seenKey)) {
                Log.d(TAG, "Debug reprocessing already-seen media: ${media.uri}")
            }
            if (!BuildConfig.DEBUG) {
                MediaScannerSettings.markSeen(applicationContext, seenKey)
            }
            Log.d(TAG, "Processing media: uri=${media.uri}, mime=${media.mimeType}, isVideo=${media.isVideo}")

            val candidates = if (media.isVideo) {
                Log.d(TAG, "Skipping ALPR for video media in background scanner")
                emptyList()
            } else {
                NativeAlprEngine.detectLicensePlates(applicationContext, media)
            }
            Log.d(TAG, "Detected ${candidates.size} plate candidate(s): ${candidates.toLogSummary()}")
            val bestPlate = candidates.bestQualifiedCandidate()
            if (bestPlate == null) {
                Log.d(TAG, "Skipping media: no plate met confidence/state threshold")
                return@forEach
            }
            Log.d(
                TAG,
                "Best plate: ${bestPlate.plate} state=${bestPlate.state} plateConfidence=${bestPlate.confidence} stateConfidence=${bestPlate.stateConfidence} plateType=${bestPlate.plateType}"
            )
            Log.d(TAG, "Running complaint inference")
            val complaintId = NativeAlprEngine.inferComplaintId(applicationContext, media)
            if (complaintId == null) {
                Log.d(TAG, "Skipping media: complaint inference did not meet threshold")
                return@forEach
            }
            Log.d(TAG, "Inferred complaint: $complaintId")
            val metadata = extractSubmissionMetadata(applicationContext, media)
            Log.d(
                TAG,
                "Metadata: occurredAt=${metadata.occurredAtIso}, lat=${metadata.latitude}, lon=${metadata.longitude}"
            )
            val reverseGeocodeSuggestion = if (metadata.latitude != null && metadata.longitude != null) {
                reverseGeocodeAddress(applicationContext, metadata.latitude, metadata.longitude)
            } else {
                null
            }
            Log.d(TAG, "Reverse geocode: ${reverseGeocodeSuggestion?.label.orEmpty()} region=${reverseGeocodeSuggestion?.region}")
            val plateRegion = bestPlate.state ?: reverseGeocodeSuggestion?.region ?: "NY"
            val occurredAtIso = metadata.occurredAtIso ?: Instant.now().toString()
            val detectedId = detectedInfractionId(
                plate = bestPlate.plate,
                state = plateRegion,
                occurredAtIso = occurredAtIso
            )
            val detected = DetectedInfraction(
                id = detectedId,
                media = media,
                plate = bestPlate.plate,
                plateRegion = plateRegion,
                plateConfidence = bestPlate.confidence,
                stateConfidence = bestPlate.stateConfidence ?: 0f,
                complaintId = complaintId,
                occurredAtIso = occurredAtIso,
                address = reverseGeocodeSuggestion?.label.orEmpty(),
                latitude = metadata.latitude,
                longitude = metadata.longitude,
                candidates = candidates
            )
            DetectedInfractionStore.save(applicationContext, detected)
            Log.d(TAG, "Saved detected infraction: id=${detected.id}")
            DetectedInfractionNotifications.showDetected(applicationContext, detected)
        }
        MediaScannerSettings.setLastScanSeconds(applicationContext, scanStarted)
        Log.d(TAG, "Media scan finished; lastScanSeconds=$scanStarted")
        return Result.success()
    }

    private companion object {
        const val TAG = "ReportedMediaScanner"
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
        candidate.meetsMediaScannerPlateThreshold() &&
            (candidate.stateConfidence ?: 0f) >= STATE_CONFIDENCE_THRESHOLD &&
            !candidate.state.isNullOrBlank()
    }.maxByOrNull { candidate ->
        val centerX = candidate.focalPointX ?: 0.5f
        val centerY = candidate.focalPointY ?: 0.5f
        val centerPenalty = sqrt((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f))
        candidate.confidence + (candidate.stateConfidence ?: 0f) - centerPenalty
    }

private fun PlateCandidate.meetsMediaScannerPlateThreshold(): Boolean {
    val requiredConfidence = if (hasPostInferredNyForHirePlate()) {
        POST_INFERENCE_PLATE_CONFIDENCE_THRESHOLD
    } else {
        STANDARD_PLATE_CONFIDENCE_THRESHOLD
    }
    return confidence >= requiredConfidence
}

private fun PlateCandidate.hasPostInferredNyForHirePlate(): Boolean =
    state == "NY" && plateType in setOf("TAXI", "TLC")

private fun List<PlateCandidate>.toLogSummary(): String =
    take(5).joinToString(prefix = "[", postfix = "]") { candidate ->
        "${candidate.plate}/${candidate.state ?: "?"}/${candidate.plateType ?: "-"} p=${candidate.confidence} s=${candidate.stateConfidence ?: 0f}"
    }

private fun detectedInfractionId(
    plate: String,
    state: String,
    occurredAtIso: String
): String {
    val key = listOf(
        plate.filter { it.isLetterOrDigit() }.uppercase(Locale.US),
        state.uppercase(Locale.US),
        occurredAtIso
    ).joinToString("|")
    return "detected-${key.hashCode().toUInt().toString(16)}"
}

private const val STANDARD_PLATE_CONFIDENCE_THRESHOLD = 0.9f
private const val POST_INFERENCE_PLATE_CONFIDENCE_THRESHOLD = 0.8f
private const val STATE_CONFIDENCE_THRESHOLD = 0.9f
private const val DEBUG_SCAN_LOOKBACK_SECONDS = 24L * 60L * 60L
