package com.reported.nativeandroid.batch

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.ComposerAction
import com.reported.nativeandroid.app.ComposerUiState
import com.reported.nativeandroid.app.ComposerValidationErrors
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.SubmissionStage
import com.reported.nativeandroid.app.UdfStore
import com.reported.nativeandroid.batch.BatchQueuedReport
import com.reported.nativeandroid.batch.BatchSubmitStore
import com.reported.nativeandroid.batch.SubmitBatchReportWorker
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.screens.AddressMapSheet
import com.reported.nativeandroid.screens.AddressSearchScreen
import com.reported.nativeandroid.screens.ComplaintInferenceResult
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.nativeandroid.screens.PlateEntryScreen
import com.reported.nativeandroid.screens.PrimaryMediaPreview
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.SecondaryButton
import com.reported.nativeandroid.screens.VerifyFieldsPanel
import com.reported.nativeandroid.screens.buildSubmissionMedia
import com.reported.nativeandroid.screens.complaintOptionsFor
import com.reported.nativeandroid.screens.extractSubmissionMetadata
import com.reported.nativeandroid.screens.reverseGeocodeAddress
import com.reported.nativeandroid.screens.searchNycAddresses
import com.reported.shared.model.Catalogs
import com.reported.shared.model.PlatePatternClassifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


fun groupBatchAnalyses(analyses: List<BatchMediaAnalysis>): List<BatchIncident> {
    val sorted = uniqueBatchAnalyses(analyses)
        .filter { it.candidates.isNotEmpty() }
        .sortedWith(compareBy<BatchMediaAnalysis> { it.occurredAtIso?.let(::parseInstantOrNull) ?: Instant.EPOCH }
            .thenBy { it.candidates.first().plate })
    val groups = mutableListOf<MutableList<BatchMediaAnalysis>>()
    sorted.forEach { analysis ->
        val candidate = analysis.candidates.first()
        val instant = analysis.occurredAtIso?.let(::parseInstantOrNull)
        val group = groups.lastOrNull()
        val canJoin = group != null && group.any { existing ->
            val existingCandidate = existing.candidates.firstOrNull()
            val existingInstant = existing.occurredAtIso?.let(::parseInstantOrNull)
            existingCandidate != null &&
                instant != null &&
                existingInstant != null &&
                shouldGroupAutoReportPhotos(existing, existingCandidate, analysis, candidate, instant, existingInstant)
        }
        if (canJoin) {
            checkNotNull(group)
            group += analysis
        } else {
            groups += mutableListOf(analysis)
        }
    }

    return groups.mapNotNull { group ->
        val bestAnalysis = bestAutoReportAnalysis(group) ?: return@mapNotNull null
        val best = bestAnalysis.candidates.firstOrNull() ?: return@mapNotNull null
        val first = group.first()
        val occurredAt = group.mapNotNull { it.occurredAtIso?.let(::parseInstantOrNull) }.minOrNull()?.toString()
            ?: Instant.now().toString()
        BatchIncident(
            plate = best.plate,
            plateRegion = best.state ?: first.region ?: "NY",
            complaintId = normalizedBatchComplaintId(group.firstNotNullOfOrNull { it.complaintId } ?: Catalogs.complaintCategories.first().id),
            occurredAtIso = occurredAt,
            address = group.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
            latitude = group.firstNotNullOfOrNull { it.latitude },
            longitude = group.firstNotNullOfOrNull { it.longitude },
            media = group.map { it.media },
            primaryMediaUri = bestAnalysis.media.uri,
            candidateGroups = group.map { analysis ->
                BatchCandidateGroup(
                    mediaUri = analysis.media.uri,
                    candidates = analysis.candidates
                )
            },
            contentHashes = group.mapNotNull { it.contentHash?.takeIf { hash -> hash.isNotBlank() } }.distinct(),
            candidates = group.flatMap { it.candidates }.sortedByDescending { it.confidence },
            complaintConfidence = group.mapNotNull { it.complaintConfidence }.maxOrNull()
        )
    }
}

fun uniqueBatchAnalyses(analyses: List<BatchMediaAnalysis>): List<BatchMediaAnalysis> {
    val seenHashes = mutableSetOf<String>()
    val seenUris = mutableSetOf<String>()
    return analyses.filter { analysis ->
        val hash = analysis.contentHash
        if (!hash.isNullOrBlank()) {
            seenHashes.add(hash)
        } else {
            seenUris.add(analysis.media.uri)
        }
    }
}

fun shouldGroupAutoReportPhotos(
    existing: BatchMediaAnalysis,
    existingCandidate: PlateCandidate,
    next: BatchMediaAnalysis,
    nextCandidate: PlateCandidate,
    nextInstant: Instant,
    existingInstant: Instant
): Boolean {
    val sameComplaint = normalizedBatchComplaintId(existing.complaintId.orEmpty()) ==
        normalizedBatchComplaintId(next.complaintId.orEmpty())
    if (!sameComplaint) return false

    val sameState = (existingCandidate.state ?: existing.region ?: "NY") ==
        (nextCandidate.state ?: next.region ?: "NY")
    if (!sameState) return false

    val plateDistance = normalizedPlateDistance(existingCandidate.plate, nextCandidate.plate)
    val similarPlate = plateDistance <= if (minOf(
            normalizedAutoReportPlate(existingCandidate.plate).length,
            normalizedAutoReportPlate(nextCandidate.plate).length
        ) >= 6
    ) 2 else 1

    val gapSeconds = abs(nextInstant.epochSecond - existingInstant.epochSecond)
    if (similarPlate) {
        val windowSeconds = if (plateDistance == 0) AutoReportExactPlateGroupingWindowSeconds else AutoReportSimilarPlateGroupingWindowSeconds
        return gapSeconds <= windowSeconds
    }
    return gapSeconds <= AutoReportLocationRescueGroupingWindowSeconds &&
        autoReportHasCloseLocation(existing, next)
}

const val AutoReportExactPlateGroupingWindowSeconds = 5 * 60L
const val AutoReportSimilarPlateGroupingWindowSeconds = 2 * 60L
const val AutoReportLocationRescueGroupingWindowSeconds = 60L
const val AutoReportLocationRescueDistanceMeters = 45.0

fun bestAutoReportAnalysis(group: List<BatchMediaAnalysis>): BatchMediaAnalysis? {
    data class PlateCluster(
        val normalizedPlate: String,
        val members: MutableList<BatchMediaAnalysis>
    )

    val clusters = mutableListOf<PlateCluster>()
    group.forEach { analysis ->
        val candidate = analysis.candidates.firstOrNull() ?: return@forEach
        val normalized = normalizedAutoReportPlate(candidate.plate)
        if (normalized.isBlank()) return@forEach
        val existingIndex = clusters.indexOfFirst { cluster ->
            val distance = normalizedPlateDistance(cluster.normalizedPlate, normalized)
            val minLength = minOf(cluster.normalizedPlate.length, normalized.length)
            distance <= if (minLength >= 6) 2 else 1
        }
        if (existingIndex >= 0) {
            clusters[existingIndex].members += analysis
        } else {
            clusters += PlateCluster(normalized, mutableListOf(analysis))
        }
    }

    return clusters
        .maxWithOrNull(
            compareBy<PlateCluster> { it.members.size }
                .thenBy { cluster -> cluster.members.mapNotNull { it.candidates.firstOrNull()?.confidence }.maxOrNull() ?: 0f }
        )
        ?.members
        ?.maxByOrNull { it.candidates.firstOrNull()?.confidence ?: 0f }
        ?: group.maxByOrNull { it.candidates.firstOrNull()?.confidence ?: 0f }
}

fun autoReportHasCloseLocation(left: BatchMediaAnalysis, right: BatchMediaAnalysis): Boolean {
    val leftLat = left.latitude
    val leftLon = left.longitude
    val rightLat = right.latitude
    val rightLon = right.longitude
    if (
        leftLat != null && leftLon != null && rightLat != null && rightLon != null &&
        usableAutoReportCoordinate(leftLat, leftLon) &&
        usableAutoReportCoordinate(rightLat, rightLon) &&
        autoReportDistanceMeters(leftLat, leftLon, rightLat, rightLon) <= AutoReportLocationRescueDistanceMeters
    ) {
        return true
    }
    val leftAddress = normalizedAutoReportAddress(left.address)
    val rightAddress = normalizedAutoReportAddress(right.address)
    return leftAddress.isNotBlank() && leftAddress == rightAddress
}

fun usableAutoReportCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        abs(latitude) <= 90.0 &&
        abs(longitude) <= 180.0 &&
        (abs(latitude) > 0.000001 || abs(longitude) > 0.000001)

fun autoReportDistanceMeters(
    leftLatitude: Double,
    leftLongitude: Double,
    rightLatitude: Double,
    rightLongitude: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val leftLatRad = Math.toRadians(leftLatitude)
    val rightLatRad = Math.toRadians(rightLatitude)
    val deltaLat = Math.toRadians(rightLatitude - leftLatitude)
    val deltaLon = Math.toRadians(rightLongitude - leftLongitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(leftLatRad) * cos(rightLatRad) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

fun normalizedAutoReportAddress(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()

fun normalizedAutoReportPlate(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()

fun normalizedPlateDistance(a: String, b: String): Int {
    val left = normalizedAutoReportPlate(a)
    val right = normalizedAutoReportPlate(b)
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { i, leftChar ->
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        right.forEachIndexed { j, rightChar ->
            val substitution = previous[j] + if (leftChar == rightChar) 0 else 1
            current[j + 1] = minOf(
                previous[j + 1] + 1,
                current[j] + 1,
                substitution
            )
        }
        previous = current
    }
    return previous[right.length]
}

fun BatchIncident.candidatesForMedia(mediaUri: String?): List<PlateCandidate> {
    if (mediaUri.isNullOrBlank()) return candidates
    return candidateGroups.firstOrNull { it.mediaUri == mediaUri }?.candidates?.takeIf { it.isNotEmpty() }
        ?: candidates
}

fun BatchIncident.selectedAutoReportCandidate(mediaUri: String?): PlateCandidate? {
    val sourceCandidates = candidatesForMedia(mediaUri)
    return sourceCandidates.firstOrNull { normalizedAutoReportPlate(it.plate) == normalizedAutoReportPlate(plate) }
        ?: sourceCandidates.firstOrNull()
        ?: candidates.firstOrNull()
}

fun BatchIncident.withAutoReportCandidate(candidate: PlateCandidate): BatchIncident =
    copy(
        plate = candidate.plate.uppercase().take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH),
        plateRegion = candidate.state?.uppercase()?.take(2) ?: plateRegion
    )

fun BatchIncident.toComposerUiState(
    addressQuery: String,
    addressSuggestions: List<AddressSuggestion>,
    addressLookupInFlight: Boolean
): ComposerUiState {
    val primary = sourceMedia
    val normalizedComplaintId = normalizedBatchComplaintId(complaintId)
    return ComposerUiState(
        stage = SubmissionStage.VERIFY,
        selectedComplaintId = normalizedComplaintId,
        primaryMedia = primary,
        extraMedia = media.filterNot { it.uri == primary?.uri },
        plateCandidates = candidatesForMedia(primary?.uri),
        selectedPlateCandidate = plate,
        latitude = latitude,
        longitude = longitude,
        addressQuery = addressQuery.ifBlank { address },
        addressSuggestions = addressSuggestions,
        photoAddressSuggestion = if (latitude != null && longitude != null && address.isNotBlank()) {
            AddressSuggestion(
                label = address,
                latitude = latitude,
                longitude = longitude,
                region = plateRegion.takeIf { it.isNotBlank() }
            )
        } else {
            null
        },
        lookupInFlight = addressLookupInFlight,
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        description = description,
        notes = notes,
        occurredAtIso = occurredAtIso,
        photoOccurredAtIso = occurredAtIso,
        complaintCategories = Catalogs.complaintCategories,
        selectedComplaintIds = listOfNotNull(normalizedComplaintId.takeIf { it.isNotBlank() }),
        validationErrors = ComposerValidationErrors(
            media = if (media.isEmpty()) "Add at least one photo or video." else null,
            complaint = if (normalizedComplaintId.isBlank()) "Choose a complaint type." else null,
            plate = if (plate.isBlank() || !PlatePatternClassifier.isValidForSubmission(plate)) "Enter a valid plate." else null,
            plateRegion = if (plateRegion.isBlank()) "Choose a state." else null,
            address = if (address.isBlank()) "Enter an address." else null,
            occurredAt = if (occurredAtIso.isBlank()) "Choose when this happened." else null
        )
    )
}

fun List<PlateCandidate>.autoReportDetectionSourceSize(): Size? =
    firstNotNullOfOrNull { candidate ->
        val width = candidate.sourceImageWidth?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
        val height = candidate.sourceImageHeight?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
        Size(width.toFloat(), height.toFloat())
    }

fun autoReportPlatePolygon(
    candidate: PlateCandidate,
    viewportSize: Size,
    imageSize: Size?,
    alignment: Alignment,
    contentScale: ContentScale,
    inflateByPx: Float
): List<Offset>? {
    val candidateSourceSize = listOf(candidate).autoReportDetectionSourceSize()
    val sourceWidth = candidateSourceSize?.width
        ?: imageSize?.width?.takeIf { it.isFinite() && it > 0f }
        ?: viewportSize.width
    val sourceHeight = candidateSourceSize?.height
        ?: imageSize?.height?.takeIf { it.isFinite() && it > 0f }
        ?: viewportSize.height
    val scale = when (contentScale) {
        ContentScale.Fit -> min(viewportSize.width / sourceWidth, viewportSize.height / sourceHeight)
        else -> max(viewportSize.width / sourceWidth, viewportSize.height / sourceHeight)
    }
    val renderedWidth = sourceWidth * scale
    val renderedHeight = sourceHeight * scale
    val bias = alignment as? BiasAlignment
    val horizontalFraction = (((bias?.horizontalBias ?: 0f) + 1f) / 2f).coerceIn(0f, 1f)
    val verticalFraction = (((bias?.verticalBias ?: 0f) + 1f) / 2f).coerceIn(0f, 1f)
    val imageLeft = -(renderedWidth - viewportSize.width) * horizontalFraction
    val imageTop = -(renderedHeight - viewportSize.height) * verticalFraction

    if (candidate.autoReportHasUsableCornerPoints()) {
        val points = (0 until 8 step 2).map { index ->
            Offset(
                x = imageLeft + candidate.cornerPoints[index].coerceIn(0f, 1f) * renderedWidth,
                y = imageTop + candidate.cornerPoints[index + 1].coerceIn(0f, 1f) * renderedHeight
            )
        }
        return if (inflateByPx > 0f) points.autoReportInflateFromCentroid(inflateByPx) else points
    }

    val left = candidate.boundsLeft ?: return null
    val top = candidate.boundsTop ?: return null
    val right = candidate.boundsRight ?: return null
    val bottom = candidate.boundsBottom ?: return null
    val rect = Rect(
        left = imageLeft + left * renderedWidth - inflateByPx,
        top = imageTop + top * renderedHeight - inflateByPx,
        right = imageLeft + right * renderedWidth + inflateByPx,
        bottom = imageTop + bottom * renderedHeight + inflateByPx
    )
    return listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
        Offset(rect.left, rect.bottom)
    )
}

fun PlateCandidate.autoReportHasUsableCornerPoints(): Boolean {
    if (cornerPoints.size < 8) return false
    val points = (0 until 8 step 2).map { index ->
        Offset(cornerPoints[index].coerceIn(0f, 1f), cornerPoints[index + 1].coerceIn(0f, 1f))
    }
    val polygonArea = abs(
        points.indices.sumOf { index ->
            val next = points[(index + 1) % points.size]
            (points[index].x * next.y - next.x * points[index].y).toDouble()
        }.toFloat()
    ) / 2f
    if (polygonArea <= 0.00002f) return false
    val widthA = sqrt(
        (points[1].x - points[0].x) * (points[1].x - points[0].x) +
            (points[1].y - points[0].y) * (points[1].y - points[0].y)
    )
    val widthB = sqrt(
        (points[2].x - points[3].x) * (points[2].x - points[3].x) +
            (points[2].y - points[3].y) * (points[2].y - points[3].y)
    )
    val heightA = sqrt(
        (points[3].x - points[0].x) * (points[3].x - points[0].x) +
            (points[3].y - points[0].y) * (points[3].y - points[0].y)
    )
    val heightB = sqrt(
        (points[2].x - points[1].x) * (points[2].x - points[1].x) +
            (points[2].y - points[1].y) * (points[2].y - points[1].y)
    )
    val averageWidth = (widthA + widthB) / 2f
    val averageHeight = (heightA + heightB) / 2f
    if (averageWidth <= 0f || averageHeight <= 0f) return false
    val aspect = averageWidth / averageHeight
    return aspect in 1.6f..8.5f
}

fun List<Offset>.autoReportInflateFromCentroid(amount: Float): List<Offset> {
    if (isEmpty() || amount <= 0f) return this
    val centroid = Offset(
        x = sumOf { it.x.toDouble() }.toFloat() / size,
        y = sumOf { it.y.toDouble() }.toFloat() / size
    )
    return map { point ->
        val dx = point.x - centroid.x
        val dy = point.y - centroid.y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.001f) {
            point
        } else {
            Offset(
                x = point.x + dx / length * amount,
                y = point.y + dy / length * amount
            )
        }
    }
}

fun List<Offset>.autoReportContainsPoint(point: Offset): Boolean {
    var inside = false
    var previous = last()
    for (current in this) {
        val intersects = ((current.y > point.y) != (previous.y > point.y)) &&
            point.x < (previous.x - current.x) * (point.y - current.y) / ((previous.y - current.y).takeIf { it != 0f } ?: 0.0001f) + current.x
        if (intersects) inside = !inside
        previous = current
    }
    return inside
}

fun BatchIncident.toQueuedReport(): BatchQueuedReport =
    BatchQueuedReport(
        id = id,
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        description = description,
        notes = notes,
        complaintId = normalizedBatchComplaintId(complaintId),
        occurredAtIso = occurredAtIso,
        latitude = latitude,
        longitude = longitude,
        media = media,
        submittedContentHashes = contentHashes
    )

data class AutoReportBatchResult(
    val totalPhotos: Int,
    val incidents: List<BatchIncident>,
    val processedPhotos: List<AutoReportProcessedPhoto>
)

data class AutoReportProcessedPhoto(
    val id: String,
    val previewUri: String,
    val resultTitle: String,
    val resultDetail: String,
    val matched: Boolean
)

data class AutoReportMediaItem(
    val media: SubmissionMedia,
    val sortTime: Long
)

enum class AutoReportScanWindow(
    val label: String,
    val buttonLabel: String,
    val sentenceLabel: String,
    val duration: Duration
) {
    OneHour("1 hr", "Last 1 Hour", "the last hour", Duration.ofHours(1)),
    EightHours("8 hr", "Last 8 Hours", "the last 8 hours", Duration.ofHours(8)),
    OneDay("1 day", "Last 1 Day", "the last day", Duration.ofDays(1)),
    ThreeDays("3 days", "Last 3 Days", "the last 3 days", Duration.ofDays(3)),
    SevenDays("7 days", "Last 7 Days", "the last 7 days", Duration.ofDays(7)),
    FourteenDays("14 days", "Last 14 Days", "the last 14 days", Duration.ofDays(14)),
    ThirtyOneDays("31 days", "Last 31 Days", "the last 31 days", Duration.ofDays(31))
}

suspend fun scanAutoReportPhotos(
    context: Context,
    window: AutoReportScanWindow,
    onProgress: (String, Float) -> Unit
): AutoReportBatchResult {
    if (!MediaScannerSettings.supportsAutoReportLibraryScan()) {
        onProgress("Photo scan unavailable", 1f)
        return AutoReportBatchResult(totalPhotos = 0, incidents = emptyList(), processedPhotos = emptyList())
    }
    val sinceInstant = Instant.now().minus(window.duration)
    val mediaItems = queryAutoReportImages(context, sinceInstant)
    if (mediaItems.isEmpty()) {
        onProgress("No recent photos found", 1f)
        return AutoReportBatchResult(totalPhotos = 0, incidents = emptyList(), processedPhotos = emptyList())
    }

    val analyses = mutableListOf<BatchMediaAnalysis>()
    val processedPhotos = mutableListOf<AutoReportProcessedPhoto>()
    mediaItems.forEachIndexed { index, item ->
        val media = item.media
        val baseProgress = index.toFloat() / mediaItems.size.toFloat()
        onProgress("Checking photo ${index + 1} of ${mediaItems.size}", baseProgress)
        val contentHash = mediaContentHash(context, media)
        if (!contentHash.isNullOrBlank() && BatchSubmitStore.hasSubmittedAutoReportContentHash(context, contentHash)) {
            return@forEachIndexed
        }
        val candidates = NativeAlprEngine.detectLicensePlates(context, media)
        val bestPlate = candidates.bestAutoReportCandidate(context)
        if (bestPlate == null) {
            val topCandidate = candidates.bestObservedAutoReportCandidate()
            processedPhotos += AutoReportProcessedPhoto(
                id = media.uri,
                previewUri = media.uri,
                resultTitle = if (candidates.isEmpty()) "No vehicle plate found" else "No qualified vehicle plate",
                resultDetail = if (candidates.isEmpty()) {
                    "Plate AI found no readable plate candidates."
                } else if (topCandidate != null) {
                    "Top plate AI: ${topCandidate.autoReportConfidenceSummary(context)}."
                } else {
                    "Plate AI found ${candidates.size} candidate${if (candidates.size == 1) "" else "s"}, but none met the threshold."
                },
                matched = false
            )
            return@forEachIndexed
        }
        val complaintInference = NativeAlprEngine.inferComplaint(context, media)
        val complaintId = normalizedBatchComplaintId(complaintInference?.complaintId.orEmpty())
        if (!complaintInference.acceptedAutoReportComplaint(context)) {
            processedPhotos += AutoReportProcessedPhoto(
                id = media.uri,
                previewUri = media.uri,
                resultTitle = "Not report-worthy",
                resultDetail = "Plate AI passed: ${bestPlate.autoReportConfidenceSummary(context)}. ${complaintInference.autoReportComplaintConfidenceSummary(context = context, matched = false)}",
                matched = false
            )
            return@forEachIndexed
        }

        val metadata = extractSubmissionMetadata(context, media)
        val address = if (metadata.latitude != null && metadata.longitude != null) {
            reverseGeocodeAddress(context, metadata.latitude, metadata.longitude)
        } else {
            null
        }
        val orderedCandidates = candidates.sortedWith(
            compareByDescending<PlateCandidate> { it.plate == bestPlate.plate && it.state == bestPlate.state }
                .thenByDescending { it.confidence }
        )
        analyses += BatchMediaAnalysis(
            media = media,
            occurredAtIso = metadata.occurredAtIso,
            latitude = metadata.latitude,
            longitude = metadata.longitude,
            address = address?.label.orEmpty(),
            region = address?.region,
            complaintId = complaintId,
            complaintConfidence = complaintInference?.confidence,
            contentHash = contentHash,
            candidates = orderedCandidates
        )
        processedPhotos += AutoReportProcessedPhoto(
            id = media.uri,
            previewUri = media.uri,
            resultTitle = "Matched ${batchComplaintLabel(complaintId)}",
            resultDetail = "Plate AI passed: ${bestPlate.autoReportConfidenceSummary(context)}. ${complaintInference.autoReportComplaintConfidenceSummary(context = context, matched = true)}",
            matched = true
        )
    }

    onProgress("Grouping possible reports", 1f)
    return AutoReportBatchResult(
        totalPhotos = processedPhotos.size,
        incidents = groupBatchAnalyses(analyses),
        processedPhotos = processedPhotos
    )
}

fun queryAutoReportImages(context: Context, since: Instant): List<AutoReportMediaItem> {
    val sinceSeconds = since.epochSecond
    val sinceMillis = since.toEpochMilli()
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.Images.Media.DATE_TAKEN
    )
    val selection = "(${MediaStore.MediaColumns.DATE_ADDED} >= ? OR ${MediaStore.Images.Media.DATE_TAKEN} >= ?)"
    val selectionArgs = arrayOf(sinceSeconds.toString(), sinceMillis.toString())
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.MediaColumns.DATE_ADDED} ASC"
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    return buildList {
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(addedColumn)
                val dateTaken = if (cursor.isNull(takenColumn)) 0L else cursor.getLong(takenColumn)
                add(
                    AutoReportMediaItem(
                        media = SubmissionMedia(
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            displayName = cursor.getString(nameColumn).orEmpty(),
                            mimeType = cursor.getString(mimeColumn).orEmpty().ifBlank { "image/jpeg" },
                            isVideo = false
                        ),
                        sortTime = if (dateTaken > 0L) dateTaken else dateAdded * 1000L
                    )
                )
            }
        }
    }.sortedBy { it.sortTime }
}

fun mediaContentHash(context: Context, media: SubmissionMedia): String? =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(Uri.parse(media.uri))?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }.getOrNull()

fun List<PlateCandidate>.bestAutoReportCandidate(context: Context): PlateCandidate? =
    filter { candidate ->
        val requiredPlateConfidence = candidate.autoReportPlateThreshold(context)
        candidate.confidence >= requiredPlateConfidence &&
            (candidate.stateConfidence ?: 0f) >= AutoReportThresholds.stateConfidence(context) &&
            !candidate.state.isNullOrBlank()
    }.maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

fun List<PlateCandidate>.bestObservedAutoReportCandidate(): PlateCandidate? =
    maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

fun PlateCandidate.autoReportPlateThreshold(context: Context): Float =
    if (state == "NY" && plateType in setOf("TAXI", "TLC")) {
        AutoReportThresholds.postInferencePlateConfidence(context)
    } else {
        AutoReportThresholds.plateConfidence(context)
    }

fun PlateCandidate.autoReportConfidenceSummary(context: Context): String =
    "${plate} ${state.orEmpty()} plate ${AutoReportThresholds.percent(confidence)} (needs ${AutoReportThresholds.percent(autoReportPlateThreshold(context))}), state ${AutoReportThresholds.percent(stateConfidence ?: 0f)} (needs ${AutoReportThresholds.percent(AutoReportThresholds.stateConfidence(context))})"

internal fun ComplaintInferenceResult?.acceptedAutoReportComplaint(context: Context): Boolean =
    this != null &&
        accepted &&
        confidence >= AutoReportThresholds.complaintConfidence(context) &&
        normalizedBatchComplaintId(complaintId) in autoReportComplaintIds

internal fun ComplaintInferenceResult?.autoReportComplaintConfidenceSummary(context: Context, matched: Boolean): String {
    val required = AutoReportThresholds.percent(AutoReportThresholds.complaintConfidence(context))
    if (this == null) {
        return "Reported infraction model returned no blocked bike lane/crosswalk detection score (needs $required)."
    }
    val label = batchComplaintLabel(complaintId)
    val confidence = AutoReportThresholds.percent(confidence)
    return if (matched) {
        "Reported infraction model: $label $confidence (needs $required), queued this photo for bulk review."
    } else {
        "Reported infraction model top score: $label $confidence (needs $required), below Auto-Report threshold."
    }
}

fun normalizedBatchComplaintId(complaintId: String): String =
    when (complaintId) {
        "blocked_bike_lane" -> "Z8vjWz8uYr"
        "blocked_crosswalk" -> "GzRxlMN1vl"
        else -> complaintId
    }

fun batchComplaintLabel(complaintId: String): String =
    Catalogs.complaintCategories.firstOrNull { it.id == normalizedBatchComplaintId(complaintId) }?.name
        ?: when (normalizedBatchComplaintId(complaintId)) {
            "Z8vjWz8uYr" -> "Blocked bike lane"
            "GzRxlMN1vl" -> "Blocked crosswalk"
            else -> "Complaint"
        }

fun BatchIncident.autoReportAiSummary(): String {
    val photoText = "${media.size} photo${if (media.size == 1) "" else "s"}"
    val plateScore = candidates.maxOfOrNull { it.confidence }?.let(AutoReportThresholds::percent) ?: "unknown"
    val stateScore = candidates.mapNotNull { it.stateConfidence }.maxOrNull()?.let(AutoReportThresholds::percent) ?: "unknown"
    val complaintScore = complaintConfidence?.let(AutoReportThresholds::percent) ?: "unknown"
    return "Reported AI grouped $photoText that appear to show ${complaintName.lowercase()} involving plate $plate in $plateRegion. Plate confidence $plateScore, state confidence $stateScore, infraction confidence $complaintScore."
}

val autoReportComplaintIds = setOf("Z8vjWz8uYr", "GzRxlMN1vl")

fun parseInstantOrNull(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()

fun formatBatchTime(value: String): String =
    parseInstantOrNull(value)
        ?.let { DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(it) }
        ?: value
