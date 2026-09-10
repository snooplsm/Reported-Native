package com.reported.nativeandroid.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import android.util.Log
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.batch.BatchSubmitStore
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.media.LocalSubmissionMediaCleaner
import com.reported.nativeandroid.media.ParseMediaUploader
import com.reported.nativeandroid.remoteconfig.ReportedRemoteConfig
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.Catalogs
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.DraftMedia
import com.reported.shared.model.DraftPlateCandidate
import com.reported.shared.model.PhiladelphiaMobilityAccessCatalogs
import com.reported.shared.model.PhiladelphiaMobilityAccessDetails
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.ReportDraft
import com.reported.shared.model.ReportFilter
import com.reported.shared.model.ReportSummary
import com.reported.shared.model.SubmitReportCommand
import com.reported.shared.model.SubmitReportMediaFile
import com.reported.shared.model.VehicleLookupDetails
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

fun DraftMedia.toSubmissionMedia() = SubmissionMedia(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    isVideo = isVideo
)

fun SubmissionMedia.toDraftMedia() = DraftMedia(
    uri = uri,
    displayName = displayName,
    mimeType = mimeType,
    isVideo = isVideo
)

fun DraftPlateCandidate.toPlateCandidate() = PlateCandidate(
    plate = plate,
    confidence = confidence,
    rawPlateText = rawPlateText,
    wasPlateCorrected = wasPlateCorrected,
    state = state,
    stateConfidence = stateConfidence,
    plateType = plateType,
    plateTypeLabel = plateTypeLabel,
    focalPointX = focalPointX,
    focalPointY = focalPointY,
    boundsLeft = boundsLeft,
    boundsTop = boundsTop,
    boundsRight = boundsRight,
    boundsBottom = boundsBottom,
    rotationDegrees = rotationDegrees,
    cornerPoints = cornerPoints,
    sourceImageWidth = sourceImageWidth,
    sourceImageHeight = sourceImageHeight,
    thumbnailUri = thumbnailUri,
    videoFramePreviewUri = videoFramePreviewUri,
    videoFrameTimeMs = videoFrameTimeMs
)

fun PlateCandidate.toDraftPlateCandidate() = DraftPlateCandidate(
    plate = plate,
    confidence = confidence,
    rawPlateText = rawPlateText,
    wasPlateCorrected = wasPlateCorrected,
    state = state,
    stateConfidence = stateConfidence,
    plateType = plateType,
    plateTypeLabel = plateTypeLabel,
    focalPointX = focalPointX,
    focalPointY = focalPointY,
    boundsLeft = boundsLeft,
    boundsTop = boundsTop,
    boundsRight = boundsRight,
    boundsBottom = boundsBottom,
    rotationDegrees = rotationDegrees,
    cornerPoints = cornerPoints,
    sourceImageWidth = sourceImageWidth,
    sourceImageHeight = sourceImageHeight,
    thumbnailUri = thumbnailUri,
    videoFramePreviewUri = videoFramePreviewUri,
    videoFrameTimeMs = videoFrameTimeMs
)

fun ComposerUiState.hasMedia(media: SubmissionMedia): Boolean =
    primaryMedia?.uri == media.uri || extraMedia.any { it.uri == media.uri }

fun ComposerUiState.vehicleLookupKey(): String? {
    val normalizedPlate = PlatePatternClassifier.normalizePlateInput(plate)
        .takeIf { it.length in 2..PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH }
        ?: return null
    val normalizedState = plateRegion.trim().uppercase()
        .takeIf { it.matches(Regex("^[A-Z]{2}$")) }
        ?: return null
    return "$normalizedState:$normalizedPlate"
}

fun PhiladelphiaMobilityAccessDetails.withoutVehicleLookupPrefill(
    details: VehicleLookupDetails
): PhiladelphiaMobilityAccessDetails = copy(
    vehicleMake = vehicleMake.takeUnless {
        it.equals(details.vehicleMake, ignoreCase = true)
    }.orEmpty(),
    vehicleModel = vehicleModel.takeUnless {
        it.equals(details.vehicleModel, ignoreCase = true)
    }.orEmpty(),
    bodyStyle = bodyStyle.takeUnless {
        it.equals(details.vehicleBody, ignoreCase = true)
    }.orEmpty()
)

const val MaxSubmissionMediaCount = 3
const val MaxSubmissionVideoCount = 1
const val MaxSubmissionMediaMessage = "You can attach up to 3 photos or videos."
const val MaxSubmissionVideoMessage = "You can attach no more than 1 video."
const val DuplicateSubmissionMediaMessage = "That photo or video is already attached."
const val VehicleClassificationDebugPrefix = "[DEBUG] Vehicle classification"

data class SubmitAnalyticsPayload(
    val objectId: String,
    val county: String,
    val plateRegion: String,
    val complaintCount: Int,
    val mediaCount: Int,
    val hasVideo: Boolean,
    val mediaToSubmitMillis: Long?
)

fun inferCounty(address: String): String {
    val normalized = address.lowercase(Locale.US)
    return when {
        normalized.contains("manhattan") ||
            normalized.contains("new york, ny") ||
            normalized.contains("new york ny") -> "New York County"
        normalized.contains("brooklyn") ||
            normalized.contains("kings county") -> "Kings County"
        normalized.contains("queens") -> "Queens County"
        normalized.contains("bronx") -> "Bronx County"
        normalized.contains("staten island") ||
            normalized.contains("richmond county") -> "Richmond County"
        normalized.contains("philadelphia") ||
            normalized.contains("philly") -> "Philadelphia County"
        else -> "unknown"
    }
}

fun ComposerUiState.mediaItems(): List<SubmissionMedia> =
    listOfNotNull(primaryMedia) + extraMedia

fun ComposerUiState.withMediaAddedTiming(previous: ComposerUiState): ComposerUiState {
    if (
        previous.mediaItems().isEmpty() &&
        previous.firstMediaAddedElapsedRealtimeMs == null &&
        mediaItems().isNotEmpty()
    ) {
        val items = mediaItems()
        ReportedAnalytics.logReportMediaAdded(
            surface = "new_report",
            mediaCount = items.size,
            hasVideo = items.any { it.isVideo }
        )
        return copy(firstMediaAddedElapsedRealtimeMs = SystemClock.elapsedRealtime())
    }
    return this
}

fun ComposerUiState.mediaToSubmitMillis(): Long? =
    firstMediaAddedElapsedRealtimeMs?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }

fun ComposerUiState.mediaLimitErrorFor(
    media: SubmissionMedia,
    replacingPrimary: Boolean
): String? {
    val existingMedia = if (replacingPrimary) extraMedia else mediaItems()
    val isPhiladelphia = isPhiladelphiaSubmission()
    return when {
        existingMedia.any { it.uri == media.uri } -> DuplicateSubmissionMediaMessage
        isPhiladelphia && media.isVideo -> PhiladelphiaSubmissionVideoMessage
        isPhiladelphia && existingMedia.any { it.isVideo } -> PhiladelphiaSubmissionVideoMessage
        isPhiladelphia && existingMedia.size + 1 > PhiladelphiaSubmissionMediaCount -> PhiladelphiaSubmissionMediaMessage
        existingMedia.size + 1 > MaxSubmissionMediaCount -> MaxSubmissionMediaMessage
        media.isVideo && existingMedia.count { it.isVideo } >= MaxSubmissionVideoCount -> MaxSubmissionVideoMessage
        else -> null
    }
}

fun ComposerUiState.withPhiladelphiaMediaValidation(): ComposerUiState {
    if (!isPhiladelphiaSubmission()) return this
    val mediaError = when {
        mediaItems().any { it.isVideo } -> PhiladelphiaSubmissionVideoMessage
        mediaItems().size > PhiladelphiaSubmissionMediaCount -> PhiladelphiaSubmissionMediaMessage
        else -> validationErrors.media
    }
    return copy(validationErrors = validationErrors.copy(media = mediaError))
}

fun ReportSummary.reportKey(): String =
    objectId.ifBlank { id.toString() }

fun ReportDraft.hasComplaintData(): Boolean =
    selectedComplaintId != null ||
        complaintIds.isNotEmpty() ||
        plate.isNotBlank() ||
        address.isNotBlank() ||
        description.isNotBlank() ||
        notes.isNotBlank() ||
        philadelphiaMobilityAccessDetails?.hasAnyValue == true ||
        occurredAtIso.isNotBlank() ||
        primaryMedia != null ||
        extraMedia.isNotEmpty() ||
        latitude != null ||
        longitude != null

fun ComposerUiState.hasComplaintData(): Boolean =
    selectedComplaintId != null ||
        selectedComplaintIds.isNotEmpty() ||
        plate.isNotBlank() ||
        address.isNotBlank() ||
        addressQuery.isNotBlank() ||
        description.isNotBlank() ||
        notes.isNotBlank() ||
        philadelphiaMobilityAccessDetails.hasAnyValue ||
        occurredAtIso.isNotBlank() ||
        primaryMedia != null ||
        extraMedia.isNotEmpty() ||
        latitude != null ||
        longitude != null

fun PhiladelphiaMobilityAccessDetails.withAddressSuggestion(
    suggestion: AddressSuggestion
): PhiladelphiaMobilityAccessDetails =
    copy(
        blockNumber = suggestion.blockNumber ?: blockNumber,
        streetName = suggestion.streetName ?: streetName,
        zipCode = suggestion.zipCode ?: zipCode
    )

fun PhiladelphiaMobilityAccessDetails.prefilledFrom(
    vehicleDescription: VehicleDescription
): PhiladelphiaMobilityAccessDetails =
    copy(
        vehicleMake = vehicleMake.ifBlank {
            PhiladelphiaMobilityAccessCatalogs.canonicalVehicleMake(vehicleDescription.make).orEmpty()
        },
        vehicleModel = vehicleModel.ifBlank { vehicleDescription.model.orEmpty() },
        vehicleColor = vehicleColor.ifBlank {
            vehicleDescription.color
                ?.let { detectedColor ->
                    PhiladelphiaMobilityAccessCatalogs.vehicleColors
                        .firstOrNull { it.equals(detectedColor, ignoreCase = true) }
                }
                .orEmpty()
        }
    )

fun ComposerUiState.submittablePhiladelphiaMobilityAccessDetails(): PhiladelphiaMobilityAccessDetails? {
    val submitAddress = addressQuery.ifBlank { address }
    if (!CityReportingRules.isPhiladelphiaReport(latitude, longitude, submitAddress)) return null
    return philadelphiaMobilityAccessDetails
        .takeIf { it.hasAnyValue }
        ?: PhiladelphiaMobilityAccessDetails()
}
