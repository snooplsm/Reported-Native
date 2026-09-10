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


data class BatchMediaAnalysis(
    val media: SubmissionMedia,
    val occurredAtIso: String?,
    val latitude: Double?,
    val longitude: Double?,
    val address: String,
    val region: String?,
    val complaintId: String?,
    val complaintConfidence: Float?,
    val contentHash: String?,
    val candidates: List<PlateCandidate>
)

data class BatchCandidateGroup(
    val mediaUri: String,
    val candidates: List<PlateCandidate>
)

data class BatchIncident(
    val id: String = UUID.randomUUID().toString(),
    val plate: String,
    val plateRegion: String,
    val complaintId: String,
    val occurredAtIso: String,
    val address: String,
    val description: String = "",
    val notes: String = "",
    val latitude: Double?,
    val longitude: Double?,
    val media: List<SubmissionMedia>,
    val primaryMediaUri: String?,
    val candidateGroups: List<BatchCandidateGroup>,
    val contentHashes: List<String>,
    val candidates: List<PlateCandidate>,
    val complaintConfidence: Float?,
    val checked: Boolean = true,
    val good: Boolean = true
) {
    val complaintName: String
        get() = Catalogs.complaintCategories.firstOrNull { it.id == normalizedBatchComplaintId(complaintId) }?.name ?: "Complaint"

    val previewUri: String?
        get() = candidates.firstOrNull()?.videoFramePreviewUri
            ?: candidates.firstOrNull()?.thumbnailUri
            ?: media.firstOrNull()?.uri

    val sourceMedia: SubmissionMedia?
        get() = primaryMediaUri?.let { preferredUri ->
            media.firstOrNull { it.uri == preferredUri }
        } ?: media.firstOrNull { !it.isVideo }
            ?: media.firstOrNull()

    val sourcePreviewUri: String?
        get() = sourceMedia?.uri
            ?: candidates.firstOrNull()?.videoFramePreviewUri
            ?: candidates.firstOrNull()?.thumbnailUri
}

val BatchIncident.isSubmittable: Boolean
    get() =
        media.isNotEmpty() &&
            normalizedBatchComplaintId(complaintId).isNotBlank() &&
            plate.isNotBlank() &&
            PlatePatternClassifier.isValidForSubmission(plate) &&
            plateRegion.isNotBlank() &&
            address.isNotBlank() &&
            occurredAtIso.isNotBlank()

data class BatchUiState(
    val incidents: List<BatchIncident> = emptyList(),
    val processedPhotos: List<AutoReportProcessedPhoto> = emptyList(),
    val selectedIndex: Int? = null,
    val deleteCandidateId: String? = null,
    val processing: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val message: String? = null,
    val autoReportWindow: AutoReportScanWindow = AutoReportScanWindow.SevenDays,
    val autoReportScanStartedElapsedRealtimeMs: Long? = null,
    val showAutoReportReview: Boolean = false
)

sealed interface BatchAction {
    data class AutoReportWindowChanged(val window: AutoReportScanWindow) : BatchAction
    data class LaunchAutoReportScan(val context: Context, val startedElapsedRealtimeMs: Long) : BatchAction
    data object CancelAutoReportScan : BatchAction
    data object AutoReportPermissionDenied : BatchAction
    data class DocumentsPicked(val context: Context, val uris: List<Uri>) : BatchAction
    data object MessageDismissed : BatchAction
    data class DeleteRequested(val incidentId: String) : BatchAction
    data object DeleteDismissed : BatchAction
    data class DeleteConfirmed(val incidentId: String) : BatchAction
    data class BatchSheetOpened(val index: Int) : BatchAction
    data object BatchSheetDismissed : BatchAction
    data class IncidentChanged(val incident: BatchIncident) : BatchAction
    data class IncidentDeleted(val incidentId: String) : BatchAction
    data class CheckedChanged(val incidentId: String, val checked: Boolean) : BatchAction
    data class GoodChanged(val incidentId: String, val good: Boolean) : BatchAction
    data class KeepChanged(val incidentId: String, val keep: Boolean) : BatchAction
    data object ReviewOpened : BatchAction
    data object ReviewDismissed : BatchAction
    data class SubmitKeptReports(val context: Context, val isAuthorized: Boolean) : BatchAction
}

sealed interface BatchEffect {
    data object RequireLogin : BatchEffect
}

class BatchViewModel : ViewModel(), UdfStore<BatchUiState, BatchAction> {
    private val _uiState = MutableStateFlow(BatchUiState())
    override val state: StateFlow<BatchUiState> = _uiState.asStateFlow()
    val uiState: StateFlow<BatchUiState> = state

    private val _effects = Channel<BatchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var autoReportScanJob: Job? = null
    private var documentPickJob: Job? = null

    override fun onAction(action: BatchAction) {
        when (action) {
            is BatchAction.AutoReportWindowChanged -> {
                _uiState.update { it.copy(autoReportWindow = action.window) }
            }
            is BatchAction.LaunchAutoReportScan -> launchAutoReportScan(
                context = action.context.applicationContext,
                scanStartedAt = action.startedElapsedRealtimeMs
            )
            BatchAction.CancelAutoReportScan -> autoReportScanJob?.cancel()
            BatchAction.AutoReportPermissionDenied -> {
                val window = _uiState.value.autoReportWindow
                val message = if (MediaScannerSettings.supportsAutoReportLibraryScan()) {
                    "Photo library permission is needed to scan ${window.sentenceLabel} without opening the gallery."
                } else {
                    "Android 13 and newer Play builds cannot scan your full photo library without broad photo access. Select photos with Add photos or videos to use Reported AI."
                }
                _uiState.update {
                    it.copy(message = message)
                }
            }
            is BatchAction.DocumentsPicked -> processPickedDocuments(action.context.applicationContext, action.uris)
            BatchAction.MessageDismissed -> _uiState.update { it.copy(message = null) }
            is BatchAction.DeleteRequested -> _uiState.update { it.copy(deleteCandidateId = action.incidentId) }
            BatchAction.DeleteDismissed -> _uiState.update { it.copy(deleteCandidateId = null) }
            is BatchAction.DeleteConfirmed -> deleteIncident(action.incidentId)
            is BatchAction.BatchSheetOpened -> _uiState.update { it.copy(selectedIndex = action.index) }
            BatchAction.BatchSheetDismissed -> _uiState.update { it.copy(selectedIndex = null) }
            is BatchAction.IncidentChanged -> updateIncident(action.incident)
            is BatchAction.IncidentDeleted -> deleteIncident(action.incidentId)
            is BatchAction.CheckedChanged -> updateIncident(action.incidentId) { it.copy(checked = action.checked) }
            is BatchAction.GoodChanged -> updateIncident(action.incidentId) { it.copy(good = action.good) }
            is BatchAction.KeepChanged -> updateIncident(action.incidentId) { it.copy(good = action.keep, checked = action.keep) }
            BatchAction.ReviewOpened -> _uiState.update { it.copy(showAutoReportReview = true) }
            BatchAction.ReviewDismissed -> _uiState.update { it.copy(showAutoReportReview = false) }
            is BatchAction.SubmitKeptReports -> submitKeptReports(action.context.applicationContext, action.isAuthorized)
        }
    }

    private fun launchAutoReportScan(context: Context, scanStartedAt: Long) {
        autoReportScanJob?.cancel()
        val window = _uiState.value.autoReportWindow
        autoReportScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processing = true,
                    progress = 0f,
                    status = "Scanning ${window.sentenceLabel}",
                    processedPhotos = emptyList(),
                    incidents = emptyList(),
                    autoReportScanStartedElapsedRealtimeMs = scanStartedAt,
                    showAutoReportReview = false,
                    message = null
                )
            }
            try {
                val result = scanAutoReportPhotos(
                    context = context,
                    window = window,
                    onProgress = { nextStatus, nextProgress ->
                        _uiState.update { it.copy(status = nextStatus, progress = nextProgress) }
                    }
                )
                _uiState.update {
                    it.copy(
                        incidents = result.incidents,
                        processedPhotos = result.processedPhotos,
                        status = "Found ${result.incidents.size} possible report${if (result.incidents.size == 1) "" else "s"}",
                        message = if (result.totalPhotos == 0) {
                            "No new photos from ${window.sentenceLabel} were available to scan."
                        } else {
                            null
                        },
                        showAutoReportReview = result.totalPhotos > 0
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        autoReportScanStartedElapsedRealtimeMs = null,
                        message = "Photo scan cancelled."
                    )
                }
            } finally {
                _uiState.update { it.copy(processing = false) }
                autoReportScanJob = null
            }
        }
    }

    private fun processPickedDocuments(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        documentPickJob?.cancel()
        documentPickJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processing = true,
                    progress = 0f,
                    status = "Preparing media",
                    message = null
                )
            }
            try {
                val analyses = mutableListOf<BatchMediaAnalysis>()
                uris.forEachIndexed { index, uri ->
                    persistDocumentRead(context, uri)
                    val media = buildSubmissionMedia(context, uri, isVideoUri(context, uri))
                    _uiState.update { it.copy(status = "Reading media ${index + 1} of ${uris.size}") }
                    val metadata = extractSubmissionMetadata(context, media)
                    val address = if (metadata.latitude != null && metadata.longitude != null) {
                        reverseGeocodeAddress(context, metadata.latitude, metadata.longitude)
                    } else {
                        null
                    }
                    val candidates = if (media.isVideo) {
                        emptyList()
                    } else {
                        NativeAlprEngine.detectLicensePlates(context, media)
                    }
                    val complaintId = if (media.isVideo) null else NativeAlprEngine.inferComplaintId(context, media)
                    analyses += BatchMediaAnalysis(
                        media = media,
                        occurredAtIso = metadata.occurredAtIso,
                        latitude = metadata.latitude,
                        longitude = metadata.longitude,
                        address = address?.label.orEmpty(),
                        region = address?.region,
                        complaintId = complaintId,
                        complaintConfidence = null,
                        contentHash = mediaContentHash(context, media),
                        candidates = candidates
                    )
                    _uiState.update { it.copy(progress = (index + 1).toFloat() / uris.size.toFloat()) }
                }
                val nextIncidents = groupBatchAnalyses(analyses)
                _uiState.update {
                    it.copy(
                        incidents = nextIncidents,
                        status = "Found ${nextIncidents.size} possible report${if (nextIncidents.size == 1) "" else "s"}"
                    )
                }
            } finally {
                _uiState.update { it.copy(processing = false) }
                documentPickJob = null
            }
        }
    }

    private fun submitKeptReports(context: Context, isAuthorized: Boolean) {
        val currentState = _uiState.value
        val kept = currentState.incidents.filter { it.checked && it.good }
        val invalidCount = kept.count { !it.isSubmittable }
        val scanToSubmitMillis = currentState.autoReportScanStartedElapsedRealtimeMs
            ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
        ReportedAnalytics.logAutoReportSummarySubmitTapped(
            reportCount = kept.size,
            mediaCount = kept.sumOf { it.media.size },
            complaintCount = kept.count { it.complaintId.isNotBlank() },
            invalidCount = invalidCount,
            isAuthorized = isAuthorized,
            scanToSubmitMillis = scanToSubmitMillis
        )
        if (!isAuthorized) {
            viewModelScope.launch { _effects.send(BatchEffect.RequireLogin) }
            return
        }
        if (invalidCount > 0) {
            _uiState.update { it.copy(message = "Complete every kept auto-report before submitting.") }
            return
        }
        val toSubmit = kept.filter { it.isSubmittable }
        ReportedAnalytics.logReportedAiBulkSubmit(
            surface = "auto_report_review",
            reportCount = toSubmit.size,
            mediaCount = toSubmit.sumOf { it.media.size },
            complaintCount = toSubmit.count { it.complaintId.isNotBlank() },
            scanToSubmitMillis = scanToSubmitMillis
        )
        toSubmit.forEach { incident ->
            val report = incident.toQueuedReport()
            BatchSubmitStore.save(context, report)
            val request = OneTimeWorkRequestBuilder<SubmitBatchReportWorker>()
                .setInputData(workDataOf(SubmitBatchReportWorker.KEY_REPORT_ID to report.id))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
        val submittedIds = toSubmit.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                incidents = it.incidents.filterNot { incident -> incident.id in submittedIds },
                showAutoReportReview = false,
                autoReportScanStartedElapsedRealtimeMs = null,
                message = "Submitting ${toSubmit.size} batch report${if (toSubmit.size == 1) "" else "s"} in the background."
            )
        }
    }

    private fun updateIncident(changed: BatchIncident) {
        updateIncident(changed.id) { changed }
    }

    private fun updateIncident(id: String, transform: (BatchIncident) -> BatchIncident) {
        _uiState.update { state ->
            state.copy(incidents = state.incidents.map { incident ->
                if (incident.id == id) transform(incident) else incident
            })
        }
    }

    private fun deleteIncident(id: String) {
        _uiState.update { state ->
            state.copy(
                incidents = state.incidents.filterNot { it.id == id },
                deleteCandidateId = null,
                selectedIndex = null
            )
        }
    }

    override fun onCleared() {
        autoReportScanJob?.cancel()
        documentPickJob?.cancel()
        super.onCleared()
    }
}

fun persistDocumentRead(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

fun isVideoUri(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri).orEmpty().lowercase()
    if (type.startsWith("video/")) return true
    if (type.startsWith("image/")) return false
    val path = uri.toString().substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp") || path.endsWith(".webm")
}
