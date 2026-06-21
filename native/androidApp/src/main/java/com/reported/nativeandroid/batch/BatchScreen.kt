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

private data class BatchMediaAnalysis(
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

private data class BatchCandidateGroup(
    val mediaUri: String,
    val candidates: List<PlateCandidate>
)

private data class BatchIncident(
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

private val BatchIncident.isSubmittable: Boolean
    get() =
        media.isNotEmpty() &&
            normalizedBatchComplaintId(complaintId).isNotBlank() &&
            plate.isNotBlank() &&
            PlatePatternClassifier.isValidForSubmission(plate) &&
            plateRegion.isNotBlank() &&
            address.isNotBlank() &&
            occurredAtIso.isNotBlank()

private data class BatchUiState(
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

private sealed interface BatchAction {
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

private sealed interface BatchEffect {
    data object RequireLogin : BatchEffect
}

private class BatchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BatchUiState())
    val uiState: StateFlow<BatchUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BatchEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var autoReportScanJob: Job? = null
    private var documentPickJob: Job? = null

    fun onAction(action: BatchAction) {
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

private fun persistDocumentRead(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun isVideoUri(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri).orEmpty().lowercase()
    if (type.startsWith("video/")) return true
    if (type.startsWith("image/")) return false
    val path = uri.toString().substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp") || path.endsWith(".webm")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BatchScreen(
    isAuthorized: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMenu: () -> Unit,
    title: String = "Batch",
    autoReportMode: Boolean = false
) {
    val context = LocalContext.current
    val batchViewModel: BatchViewModel = viewModel()
    val uiState by batchViewModel.uiState.collectAsState()
    val incidents = uiState.incidents
    val processedPhotos = uiState.processedPhotos
    val selectedIndex = uiState.selectedIndex
    val deleteCandidate = incidents.firstOrNull { it.id == uiState.deleteCandidateId }
    val processing = uiState.processing
    val progress = uiState.progress
    val status = uiState.status
    val message = uiState.message
    val autoReportWindow = uiState.autoReportWindow
    val showAutoReportReview = uiState.showAutoReportReview
    var pendingAutoReportScanStartedAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(batchViewModel) {
        batchViewModel.effects.collect { effect ->
            when (effect) {
                BatchEffect.RequireLogin -> onRequireLogin()
            }
        }
    }

    val autoReportPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaScannerSettings.hasAutoReportPermissions(context)) {
            batchViewModel.onAction(
                BatchAction.LaunchAutoReportScan(
                    context = context,
                    startedElapsedRealtimeMs = pendingAutoReportScanStartedAt ?: SystemClock.elapsedRealtime()
                )
            )
        } else {
            batchViewModel.onAction(BatchAction.AutoReportPermissionDenied)
        }
        pendingAutoReportScanStartedAt = null
    }

    fun requestAutoReportScan() {
        val scanStartedAt = SystemClock.elapsedRealtime()
        pendingAutoReportScanStartedAt = scanStartedAt
        if (!MediaScannerSettings.supportsAutoReportLibraryScan()) {
            batchViewModel.onAction(BatchAction.AutoReportPermissionDenied)
            pendingAutoReportScanStartedAt = null
            return
        }
        ReportedAnalytics.logAutoReportScanStarted(scanWindow = autoReportWindow.name)
        if (MediaScannerSettings.hasAutoReportPermissions(context)) {
            batchViewModel.onAction(
                BatchAction.LaunchAutoReportScan(
                    context = context,
                    startedElapsedRealtimeMs = scanStartedAt
                )
            )
            pendingAutoReportScanStartedAt = null
        } else {
            autoReportPermissionLauncher.launch(MediaScannerSettings.autoReportPermissions())
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        batchViewModel.onAction(BatchAction.DocumentsPicked(context, uris))
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { batchViewModel.onAction(BatchAction.MessageDismissed) },
            confirmButton = {
                TextButton(onClick = { batchViewModel.onAction(BatchAction.MessageDismissed) }) {
                    Text("OK")
                }
            },
            title = { Text(title) },
            text = { Text(it) }
        )
    }

    deleteCandidate?.let { incident ->
        AlertDialog(
            onDismissRequest = { batchViewModel.onAction(BatchAction.DeleteDismissed) },
            title = { Text("Delete batch report?") },
            text = { Text("${incident.plate} - ${incident.plateRegion} will be removed from this batch.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        batchViewModel.onAction(BatchAction.DeleteConfirmed(incident.id))
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { batchViewModel.onAction(BatchAction.DeleteDismissed) }) { Text("Cancel") }
            }
        )
    }

    selectedIndex?.let { index ->
        if (incidents.isNotEmpty()) {
            BatchIncidentSheet(
                incidents = incidents,
                initialIndex = index.coerceIn(0, incidents.lastIndex),
                onDismiss = { batchViewModel.onAction(BatchAction.BatchSheetDismissed) },
                onIncidentChanged = { changed ->
                    batchViewModel.onAction(BatchAction.IncidentChanged(changed))
                },
                onDelete = { deleted ->
                    batchViewModel.onAction(BatchAction.IncidentDeleted(deleted.id))
                }
            )
        }
    }

    if (autoReportMode && processing) {
        AutoReportScanProgressSheet(
            status = status.ifBlank { "Preparing photo scan" },
            progress = progress,
            onCancel = { batchViewModel.onAction(BatchAction.CancelAutoReportScan) }
        )
    }

    if (autoReportMode && showAutoReportReview) {
        AutoReportReviewSheet(
            incidents = incidents,
            processedPhotos = processedPhotos,
            onDismiss = { batchViewModel.onAction(BatchAction.ReviewDismissed) },
            onIncidentChanged = { changed ->
                batchViewModel.onAction(BatchAction.IncidentChanged(changed))
            },
            onKeepChange = { incident, keep ->
                batchViewModel.onAction(BatchAction.KeepChanged(incident.id, keep))
            },
            onSubmit = { batchViewModel.onAction(BatchAction.SubmitKeptReports(context, isAuthorized)) }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    enabled = !processing,
                    onClick = {
                        if (autoReportMode) {
                            requestAutoReportScan()
                        } else {
                            picker.launch(arrayOf("image/*", "video/*"))
                        }
                    }
                ) {
                    Text(if (autoReportMode) "Scan Photos" else "Select")
                }
            }

            if (autoReportMode) {
                AutoReportTutorialCard(
                    enabled = !processing,
                    scanWindow = autoReportWindow,
                    onScanWindowSelected = { batchViewModel.onAction(BatchAction.AutoReportWindowChanged(it)) },
                    onScan = ::requestAutoReportScan
                )
            }

            if (processing && !autoReportMode) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(progress = { progress })
                        Text(status, modifier = Modifier.weight(1f))
                    }
                }
            }

            BatchSummaryCard(
                incidents = incidents,
                emptyText = if (autoReportMode) {
                    "Scan your recent photos to build a bulk review."
                } else {
                    "Select photos to build a batch."
                }
            )

            if (autoReportMode && (incidents.isNotEmpty() || processedPhotos.isNotEmpty())) {
                SecondaryButton(
                    text = "Review Results",
                    onClick = { batchViewModel.onAction(BatchAction.ReviewOpened) },
                    enabled = !processing
                )
            }

            if (!autoReportMode && incidents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Submit", style = MaterialTheme.typography.labelLarge)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (!autoReportMode) {
                    items(incidents, key = { it.id }) { incident ->
                        BatchIncidentRow(
                            incident = incident,
                            onClick = {
                                batchViewModel.onAction(
                                    BatchAction.BatchSheetOpened(incidents.indexOfFirst { it.id == incident.id })
                                )
                            },
                            onLongPress = { batchViewModel.onAction(BatchAction.DeleteRequested(incident.id)) },
                            onCheckedChange = { checked ->
                                batchViewModel.onAction(BatchAction.CheckedChanged(incident.id, checked))
                            },
                            onGoodChange = { good ->
                                batchViewModel.onAction(BatchAction.GoodChanged(incident.id, good))
                            }
                        )
                    }
                }
            }

            val submitCount = incidents.count { it.checked && it.good }
            if (!autoReportMode) {
                PrimaryButton(
                    text = "Submit $submitCount Report${if (submitCount == 1) "" else "s"}",
                    enabled = submitCount > 0 && !processing,
                    onClick = { batchViewModel.onAction(BatchAction.SubmitKeptReports(context, isAuthorized)) }
                )
            }
        }
    }
}

@Composable
private fun AutoReportTutorialCard(
    enabled: Boolean,
    scanWindow: AutoReportScanWindow,
    onScanWindowSelected: (AutoReportScanWindow) -> Unit,
    onScan: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Scan photos into a bulk review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Reported will scan ${scanWindow.sentenceLabel} of photos on this device and use on-device AI to look for vehicle photos that appear to show a blocked bike lane or blocked crosswalk. You review the bulk results before anything is submitted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AutoReportScanWindowSpinner(
                    enabled = enabled,
                    scanWindow = scanWindow,
                    onScanWindowSelected = onScanWindowSelected,
                    modifier = Modifier
                        .widthIn(min = 124.dp)
                        .weight(1f)
                )
                PrimaryButton(
                    text = "Scan Photos",
                    enabled = enabled,
                    onClick = onScan,
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}

@Composable
private fun AutoReportScanWindowSpinner(
    enabled: Boolean,
    scanWindow: AutoReportScanWindow,
    onScanWindowSelected: (AutoReportScanWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = AutoReportScanWindow.entries
    val selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f).toArgb()
    val selectedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val dropdownTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val dropdownBackgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    AndroidView(
        modifier = modifier.height(52.dp),
        factory = { context ->
            Spinner(context, Spinner.MODE_DIALOG)
        },
        update = { spinner ->
            val selectedIndex = options.indexOf(scanWindow).coerceAtLeast(0)
            spinner.onItemSelectedListener = null
            spinner.setBackgroundColor(selectedBackgroundColor)
            spinner.adapter = object : ArrayAdapter<String>(
                spinner.context,
                android.R.layout.simple_spinner_item,
                options.map { it.label }
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return styleSelectedView(super.getView(position, convertView, parent))
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return styleDropdownView(super.getDropDownView(position, convertView, parent))
                }

                private fun styleSelectedView(view: View): View {
                    (view as? TextView)?.apply {
                        gravity = Gravity.CENTER_VERTICAL
                        includeFontPadding = false
                        setSingleLine()
                        textSize = 16f
                        setTextColor(if (enabled) selectedTextColor else disabledTextColor)
                        setBackgroundColor(selectedBackgroundColor)
                        setPadding(0, 0, 0, 0)
                    }
                    return view
                }

                private fun styleDropdownView(view: View): View {
                    (view as? TextView)?.apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setSingleLine()
                        textSize = 16f
                        setTextColor(dropdownTextColor)
                        setBackgroundColor(dropdownBackgroundColor)
                    }
                    return view
                }
            }.also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            if (spinner.selectedItemPosition != selectedIndex) {
                spinner.setSelection(selectedIndex, false)
            }
            spinner.isEnabled = enabled
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = options.getOrNull(position) ?: return
                    if (selected != scanWindow) {
                        onScanWindowSelected(selected)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    )
}

@Composable
private fun BatchSummaryCard(
    incidents: List<BatchIncident>,
    emptyText: String
) {
    val counts = incidents.groupingBy { it.complaintName }.eachCount()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "${incidents.size} possible report${if (incidents.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (counts.isEmpty()) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                counts.entries.sortedBy { it.key }.forEach { (complaint, count) ->
                    Text("$complaint: $count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BatchIncidentRow(
    incident: BatchIncident,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onGoodChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 86.dp, height = 64.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                incident.previewUri?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Batch report preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(incident.complaintName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${incident.plate} - ${incident.plateRegion}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBatchTime(incident.occurredAtIso), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Good",
                        modifier = Modifier.clickable { onGoodChange(true) },
                        color = if (incident.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Bad",
                        modifier = Modifier.clickable { onGoodChange(false) },
                        color = if (!incident.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Checkbox(checked = incident.checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AutoReportProcessedPhotoRow(photo: AutoReportProcessedPhoto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (photo.matched) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 74.dp, height = 56.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    model = photo.previewUri,
                    contentDescription = "Processed photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    photo.resultTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (photo.matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    photo.resultDetail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoReportScanProgressSheet(
    status: String,
    progress: Float,
    onCancel: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Scanning Photos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) })
                Text(status, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SecondaryButton(text = "Cancel", onClick = onCancel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AutoReportReviewSheet(
    incidents: List<BatchIncident>,
    processedPhotos: List<AutoReportProcessedPhoto>,
    onDismiss: () -> Unit,
    onIncidentChanged: (BatchIncident) -> Unit,
    onKeepChange: (BatchIncident, Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val pageCount = incidents.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val submitCount = incidents.count { it.checked && it.good }
    val invalidKeptCount = incidents.count { it.checked && it.good && !it.isSubmittable }
    val currentPage = pagerState.currentPage.coerceIn(0, maxOf(0, pageCount - 1))
    val summarySignature = remember(incidents) { incidents.joinToString("|") { it.id } }
    var loggedSummarySignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentPage, summarySignature) {
        if (currentPage == incidents.size && incidents.isNotEmpty() && loggedSummarySignature != summarySignature) {
            loggedSummarySignature = summarySignature
            ReportedAnalytics.logAutoReportSummary(
                count = incidents.size,
                keptCount = submitCount,
                discardedCount = maxOf(0, incidents.size - submitCount),
                invalidCount = invalidKeptCount,
                mediaCount = incidents.sumOf { it.media.size },
                processedPhotoCount = processedPhotos.size
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Review Auto-Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append("$submitCount kept, ${maxOf(0, incidents.size - submitCount)} discarded")
                        if (invalidKeptCount > 0) append(", $invalidKeptCount needs edits")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                AutoReportReviewCarouselPage {
                    if (page < incidents.size) {
                        AutoReportIncidentReviewCard(
                            incident = incidents[page],
                            onIncidentChanged = onIncidentChanged
                        )
                    } else {
                        AutoReportSubmissionSummaryCard(
                            incidents = incidents,
                            processedPhotos = processedPhotos,
                            onIncidentSelected = { selectedIncident ->
                                val targetIndex = incidents.indexOfFirst { it.id == selectedIncident.id }
                                if (targetIndex >= 0) {
                                    scope.launch { pagerState.animateScrollToPage(targetIndex) }
                                }
                            }
                        )
                    }
                }
            }
            AutoReportReviewActions(
                currentPage = currentPage,
                incidents = incidents,
                submitCount = submitCount,
                invalidKeptCount = invalidKeptCount,
                onKeepChange = onKeepChange,
                onSubmit = onSubmit
            )
            AutoReportReviewPageIndicator(
                pageCount = pageCount,
                currentPage = currentPage,
                summaryPage = incidents.size,
                onSummary = {
                    scope.launch { pagerState.animateScrollToPage(incidents.size) }
                }
            )
        }
    }
}

@Composable
private fun AutoReportReviewCarouselPage(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f),
        border = BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.46f))
    ) {
        content()
    }
}

@Composable
private fun AutoReportReviewActions(
    currentPage: Int,
    incidents: List<BatchIncident>,
    submitCount: Int,
    invalidKeptCount: Int,
    onKeepChange: (BatchIncident, Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    if (currentPage < incidents.size) {
        val incident = incidents[currentPage]
        val isKept = incident.good && incident.checked
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = "Keep",
                onClick = {
                    ReportedAnalytics.logAutoReportDecision(
                        keep = true,
                        reportIndex = currentPage + 1,
                        reportCount = incidents.size,
                        keptCount = autoReportKeptCountAfter(incidents, incident.id, keep = true),
                        mediaCount = incident.media.size
                    )
                    onKeepChange(incident, true)
                },
                enabled = !isKept,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 11.dp)
            )
            SecondaryButton(
                text = "Discard",
                onClick = {
                    ReportedAnalytics.logAutoReportDecision(
                        keep = false,
                        reportIndex = currentPage + 1,
                        reportCount = incidents.size,
                        keptCount = autoReportKeptCountAfter(incidents, incident.id, keep = false),
                        mediaCount = incident.media.size
                    )
                    onKeepChange(incident, false)
                },
                enabled = isKept,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        PrimaryButton(
            text = "Submit $submitCount Report${if (submitCount == 1) "" else "s"}",
            onClick = onSubmit,
            enabled = submitCount > 0 && invalidKeptCount == 0,
            modifier = Modifier.padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        )
    }
}

private fun autoReportKeptCountAfter(
    incidents: List<BatchIncident>,
    incidentId: String,
    keep: Boolean
): Int = incidents.count { incident ->
    if (incident.id == incidentId) keep else incident.checked && incident.good
}

@Composable
private fun AutoReportReviewPageIndicator(
    pageCount: Int,
    currentPage: Int,
    summaryPage: Int,
    onSummary: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentPage) 9.dp else 7.dp),
                shape = CircleShape,
                color = if (index == currentPage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                },
                content = {}
            )
        }
        TextButton(
            onClick = onSummary,
            enabled = currentPage != summaryPage,
            modifier = Modifier.padding(start = 10.dp)
        ) {
            Text("Summary")
        }
    }
}

@Composable
private fun AutoReportIncidentReviewCard(
    incident: BatchIncident,
    onIncidentChanged: (BatchIncident) -> Unit
) {
    var showComplaints by remember(incident.id) { mutableStateOf(false) }
    var showPlateEntryScreen by remember(incident.id) { mutableStateOf(false) }
    var showAddressSearchScreen by remember(incident.id) { mutableStateOf(false) }
    var showAddressMap by remember(incident.id) { mutableStateOf(false) }
    var addressQuery by remember(incident.id, incident.address) { mutableStateOf(incident.address) }
    var addressSuggestions by remember(incident.id) { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var addressLookupInFlight by remember(incident.id) { mutableStateOf(false) }
    val complaintOptions = remember { complaintOptionsFor(Catalogs.complaintCategories) }
    val composerState = incident.toComposerUiState(
        addressQuery = addressQuery,
        addressSuggestions = addressSuggestions,
        addressLookupInFlight = addressLookupInFlight
    )
    val selectedCandidate = incident.selectedAutoReportCandidate(incident.sourceMedia?.uri)
    val isKept = incident.good && incident.checked

    fun handleComposerAction(action: ComposerAction) {
        when (action) {
            is ComposerAction.FieldsChanged -> {
                val nextAddress = action.address ?: incident.address
                if (action.address != null) {
                    addressQuery = nextAddress
                }
                onIncidentChanged(
                    incident.copy(
                        plate = action.plate?.uppercase()?.take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH) ?: incident.plate,
                        plateRegion = action.plateRegion?.uppercase()?.take(2) ?: incident.plateRegion,
                        address = nextAddress,
                        description = action.description ?: incident.description,
                        notes = action.notes ?: incident.notes,
                        occurredAtIso = action.occurredAtIso ?: incident.occurredAtIso
                    )
                )
            }
            is ComposerAction.SelectedComplaintChanged -> {
                onIncidentChanged(incident.copy(complaintId = action.complaintId))
            }
            is ComposerAction.PlateCandidateChosen -> {
                onIncidentChanged(incident.withAutoReportCandidate(action.candidate))
            }
            is ComposerAction.AddressQueryChanged -> {
                addressQuery = action.value
            }
            is ComposerAction.AddressSuggestionsChanged -> {
                addressSuggestions = action.suggestions
                addressLookupInFlight = action.loading
            }
            is ComposerAction.AddressLookupLoadingChanged -> {
                addressLookupInFlight = action.loading
            }
            is ComposerAction.AddressChosen -> {
                addressQuery = action.suggestion.label
                onIncidentChanged(
                    incident.copy(
                        address = action.suggestion.label,
                        latitude = action.suggestion.latitude,
                        longitude = action.suggestion.longitude,
                        plateRegion = action.suggestion.region ?: incident.plateRegion
                    )
                )
                showAddressSearchScreen = false
                showAddressMap = false
            }
            else -> Unit
        }
    }

    LaunchedEffect(showAddressSearchScreen, addressQuery, incident.address) {
        if (!showAddressSearchScreen || addressQuery.isBlank() || addressQuery == incident.address) {
            addressSuggestions = emptyList()
            addressLookupInFlight = false
            return@LaunchedEffect
        }
        addressLookupInFlight = true
        delay(250)
        addressSuggestions = searchNycAddresses(addressQuery)
        addressLookupInFlight = false
    }

    if (showComplaints) {
        AlertDialog(
            onDismissRequest = { showComplaints = false },
            title = { Text("Change complaint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    complaintOptions.forEach { option ->
                        Text(
                            text = option.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    handleComposerAction(ComposerAction.SelectedComplaintChanged(option.id))
                                    showComplaints = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showComplaints = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlateEntryScreen) {
        Dialog(
            onDismissRequest = { showPlateEntryScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PlateEntryScreen(
                state = composerState,
                onAction = ::handleComposerAction,
                onBack = { showPlateEntryScreen = false },
                onCandidateSelected = { candidate ->
                    handleComposerAction(ComposerAction.PlateCandidateChosen(candidate))
                    showPlateEntryScreen = false
                }
            )
        }
    }

    if (showAddressSearchScreen) {
        Dialog(
            onDismissRequest = { showAddressSearchScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AddressSearchScreen(
                state = composerState,
                onAction = ::handleComposerAction,
                onBack = { showAddressSearchScreen = false },
                onOpenMap = { showAddressMap = true }
            )
        }
    }

    if (showAddressMap) {
        AddressMapSheet(
            initialLatLng = LatLng(incident.latitude ?: 40.7128, incident.longitude ?: -74.0060),
            initialAddress = addressQuery.ifBlank { incident.address },
            photoAddressSuggestion = composerState.photoAddressSuggestion,
            onDismiss = { showAddressMap = false },
            onLocationSettled = { suggestion ->
                handleComposerAction(ComposerAction.AddressChosen(suggestion))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        incident.sourceMedia?.let { primaryMedia ->
            PrimaryMediaPreview(
                primaryMedia = primaryMedia,
                extraMedia = incident.media.filterNot { it.uri == primaryMedia.uri },
                primaryFocalPointX = selectedCandidate?.focalPointX,
                primaryFocalPointY = selectedCandidate?.focalPointY,
                plateCandidates = composerState.plateCandidates,
                selectedCandidate = selectedCandidate,
                selectedPlate = incident.plate,
                showRemoveButton = false,
                onPlateCandidateTapped = { candidate ->
                    handleComposerAction(ComposerAction.PlateCandidateChosen(candidate))
                },
                onPlateCandidateConfirmed = { candidate ->
                    handleComposerAction(ComposerAction.PlateCandidateChosen(candidate))
                },
                onShowPlateCandidates = { showPlateEntryScreen = true },
                onRemoveMedia = {}
            )
        }
        VerifyFieldsPanel(
            state = composerState,
            complaintOptions = complaintOptions,
            isLandscape = false,
            onAction = ::handleComposerAction,
            onShowComplaintChooser = { showComplaints = true },
            onShowPlateCandidates = { showPlateEntryScreen = true },
            onShowAddressSearch = {
                addressQuery = incident.address
                showAddressSearchScreen = true
            },
            onShowAddressMap = { showAddressMap = true }
        )
        AutoReportIncidentPhotoStrip(incident = incident)
        AutoReportReviewField("AI Summary", incident.autoReportAiSummary())
        Text(
            text = if (isKept && incident.isSubmittable) "Ready to submit" else if (isKept) "Needs edits before submission" else "Discarded",
            color = if (isKept && incident.isSubmittable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AutoReportMainPhotoPreview(
    incident: BatchIncident,
    onIncidentChanged: (BatchIncident) -> Unit
) {
    var showFullScreen by remember(incident.id) { mutableStateOf(false) }
    val sourceMedia = incident.sourceMedia
    val sourceUri = sourceMedia?.uri ?: incident.sourcePreviewUri
    val overlayCandidates = remember(incident.id, sourceUri, incident.candidateGroups, incident.candidates) {
        incident.candidatesForMedia(sourceUri)
    }
    var imageSize by remember(incident.id, sourceUri) { mutableStateOf<Size?>(null) }
    val selectedCandidate = remember(incident.id, incident.plate, sourceUri, overlayCandidates) {
        incident.selectedAutoReportCandidate(sourceUri)
    }
    val imageAlignment = selectedCandidate?.let { candidate ->
        val x = candidate.focalPointX ?: 0.5f
        val y = candidate.focalPointY ?: 0.5f
        BiasAlignment(
            horizontalBias = (x * 2f - 1f).coerceIn(-1f, 1f),
            verticalBias = (y * 2f - 1f).coerceIn(-1f, 1f)
        )
    } ?: Alignment.Center
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box {
            sourceUri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Auto-report preview",
                    contentScale = ContentScale.Crop,
                    alignment = imageAlignment,
                    onSuccess = { state ->
                        val intrinsicSize = state.painter.intrinsicSize
                        if (
                            intrinsicSize.width.isFinite() &&
                            intrinsicSize.height.isFinite() &&
                            intrinsicSize.width > 0f &&
                            intrinsicSize.height > 0f
                        ) {
                            imageSize = intrinsicSize
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showFullScreen = true }
                )
                AutoReportPlateBoundsOverlay(
                    candidates = overlayCandidates,
                    selectedPlate = incident.plate,
                    imageSize = overlayCandidates.autoReportDetectionSourceSize() ?: imageSize,
                    alignment = imageAlignment,
                    contentScale = ContentScale.Crop,
                    onCandidateTapped = { candidate ->
                        onIncidentChanged(incident.withAutoReportCandidate(candidate))
                    },
                    onEmptyTap = { showFullScreen = true }
                )
            }
        }
    }
    if (showFullScreen) {
        if (sourceMedia != null) {
            AutoReportFullScreenPhotoViewer(
                media = sourceMedia,
                incident = incident,
                onIncidentChanged = onIncidentChanged,
                onDismiss = { showFullScreen = false }
            )
        }
    }
}

@Composable
private fun AutoReportFullScreenPhotoViewer(
    media: SubmissionMedia,
    incident: BatchIncident,
    onIncidentChanged: (BatchIncident) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember(media.uri) { mutableStateOf(1f) }
    var offsetX by remember(media.uri) { mutableStateOf(0f) }
    var offsetY by remember(media.uri) { mutableStateOf(0f) }
    var imageSize by remember(media.uri) { mutableStateOf<Size?>(null) }
    val overlayCandidates = remember(incident.id, media.uri, incident.candidateGroups, incident.candidates) {
        incident.candidatesForMedia(media.uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(media.uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = nextScale
                            if (nextScale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val intrinsicSize = state.painter.intrinsicSize
                        if (
                            intrinsicSize.width.isFinite() &&
                            intrinsicSize.height.isFinite() &&
                            intrinsicSize.width > 0f &&
                            intrinsicSize.height > 0f
                        ) {
                            imageSize = intrinsicSize
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                AutoReportPlateBoundsOverlay(
                    candidates = overlayCandidates,
                    selectedPlate = incident.plate,
                    imageSize = overlayCandidates.autoReportDetectionSourceSize() ?: imageSize,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Fit,
                    onCandidateTapped = { candidate ->
                        onIncidentChanged(incident.withAutoReportCandidate(candidate))
                    },
                    onEmptyTap = {}
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 12.dp, end = 12.dp)
                    .size(52.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close image",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoReportPlateBoundsOverlay(
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    imageSize: Size?,
    alignment: Alignment,
    contentScale: ContentScale,
    onCandidateTapped: (PlateCandidate) -> Unit,
    onEmptyTap: () -> Unit
) {
    if (candidates.isEmpty()) return
    val density = LocalDensity.current
    val normalStroke = with(density) { 2.dp.toPx() }
    val selectedStroke = with(density) { 3.dp.toPx() }
    val touchSlopPx = with(density) { 10.dp.toPx() }
    val selectedColor = Color(0xFF20B15A)
    val fallbackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(candidates, imageSize, alignment, contentScale) {
                detectTapGestures { offset ->
                    val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                    val tappedCandidate = candidates.asReversed().firstOrNull { candidate ->
                        autoReportPlatePolygon(
                            candidate = candidate,
                            viewportSize = viewportSize,
                            imageSize = imageSize,
                            alignment = alignment,
                            contentScale = contentScale,
                            inflateByPx = touchSlopPx
                        )?.autoReportContainsPoint(offset) == true
                    }
                    if (tappedCandidate != null) {
                        onCandidateTapped(tappedCandidate)
                    } else {
                        onEmptyTap()
                    }
                }
            }
    ) {
        candidates.forEach { candidate ->
            val points = autoReportPlatePolygon(
                candidate = candidate,
                viewportSize = size,
                imageSize = imageSize,
                alignment = alignment,
                contentScale = contentScale,
                inflateByPx = 0f
            ) ?: return@forEach
            val isSelected = normalizedAutoReportPlate(candidate.plate) == normalizedAutoReportPlate(selectedPlate.orEmpty())
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(
                path = path,
                color = if (isSelected) selectedColor else fallbackColor,
                style = Stroke(width = if (isSelected) selectedStroke else normalStroke)
            )
        }
    }
}

@Composable
private fun AutoReportSelectionField(
    label: String,
    value: String,
    isError: Boolean,
    onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Text(
                value.ifBlank { "Select" },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun AutoReportPlateCandidateStrip(
    incident: BatchIncident,
    onIncidentChanged: (BatchIncident) -> Unit
) {
    if (incident.candidates.isEmpty()) return
    val sourceUri = incident.sourceMedia?.uri ?: incident.sourcePreviewUri
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "Detected plates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(incident.candidates.take(8), key = { "${it.plate}-${it.state}-${it.confidence}" }) { candidate ->
                Surface(
                    modifier = Modifier
                        .size(width = 92.dp, height = 54.dp)
                        .clickable {
                            onIncidentChanged(
                                incident.copy(
                                    plate = candidate.plate.uppercase().take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH),
                                    plateRegion = candidate.state?.uppercase()?.take(2) ?: incident.plateRegion
                                )
                            )
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        if (normalizedAutoReportPlate(candidate.plate) == normalizedAutoReportPlate(incident.plate)) 2.dp else 1.dp,
                        if (normalizedAutoReportPlate(candidate.plate) == normalizedAutoReportPlate(incident.plate)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val fallbackUri = candidate.videoFramePreviewUri ?: sourceUri
                        var showFallback by remember(candidate.thumbnailUri, fallbackUri) {
                            mutableStateOf(candidate.thumbnailUri.isNullOrBlank())
                        }
                        var imageFailed by remember(candidate.thumbnailUri, fallbackUri) { mutableStateOf(false) }
                        val imageUri = if (showFallback) fallbackUri else candidate.thumbnailUri
                        val imageAlignment = if (showFallback && imageUri == sourceUri) {
                            val x = candidate.focalPointX ?: 0.5f
                            val y = candidate.focalPointY ?: 0.5f
                            BiasAlignment(
                                horizontalBias = (x * 2f - 1f).coerceIn(-1f, 1f),
                                verticalBias = (y * 2f - 1f).coerceIn(-1f, 1f)
                            )
                        } else {
                            Alignment.Center
                        }
                        if (imageUri != null && !imageFailed) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Detected plate ${candidate.plate}",
                                contentScale = ContentScale.Crop,
                                alignment = imageAlignment,
                                onError = {
                                    if (!showFallback && fallbackUri != null) {
                                        showFallback = true
                                        imageFailed = false
                                    } else {
                                        imageFailed = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            candidate.plate,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReportIncidentPhotoStrip(incident: BatchIncident) {
    val previews = incident.media.map { it.uri }.ifEmpty { listOfNotNull(incident.previewUri) }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "${previews.size} processed photo${if (previews.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(previews, key = { it }) { uri ->
                Surface(
                    modifier = Modifier.size(width = 84.dp, height = 64.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Processed auto-report photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoReportReviewField(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "Not set" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AutoReportSubmissionSummaryCard(
    incidents: List<BatchIncident>,
    processedPhotos: List<AutoReportProcessedPhoto>,
    onIncidentSelected: (BatchIncident) -> Unit
) {
    val kept = incidents.filter { it.checked && it.good }
    val keptPhotos = kept.sumOf { it.media.size }
    val invalidKept = kept.filter { !it.isSubmittable }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Submission Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AutoReportReviewField(
                "Reports to Submit",
                "${kept.size} report${if (kept.size == 1) "" else "s"} from $keptPhotos photo${if (keptPhotos == 1) "" else "s"}"
            )
            AutoReportReviewField(
                "Processed Photos",
                "${processedPhotos.size} scanned, ${processedPhotos.count { it.matched }} matched, ${processedPhotos.count { !it.matched }} not queued"
            )
            if (invalidKept.isNotEmpty()) {
                AutoReportReviewField(
                    "Needs Edits",
                    "${invalidKept.size} kept report${if (invalidKept.size == 1) "" else "s"} must be completed before submission."
                )
            }
            if (kept.isEmpty()) {
                Text("No reports are currently marked Keep.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                kept.forEach { incident ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIncidentSelected(incident) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(incident.complaintName, fontWeight = FontWeight.Bold)
                            Text(
                                if (incident.isSubmittable) "Ready" else "Needs edits",
                                color = if (incident.isSubmittable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "${incident.media.size} photo${if (incident.media.size == 1) "" else "s"} • ${incident.autoReportAiSummary()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${incident.plate} - ${incident.plateRegion} • ${formatBatchTime(incident.occurredAtIso)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            AutoReportPlateCandidateStrip(incident = incident, onIncidentChanged = {})
                            if (incident.address.isNotBlank()) {
                                Text(
                                    incident.address,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoReportIncidentCarousel(
    incidents: List<BatchIncident>,
    onClick: (BatchIncident) -> Unit,
    onKeepChange: (BatchIncident, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Review reports",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(incidents, key = { it.id }) { incident ->
                AutoReportIncidentCard(
                    incident = incident,
                    onClick = { onClick(incident) },
                    onKeep = { onKeepChange(incident, true) },
                    onDiscard = { onKeepChange(incident, false) }
                )
            }
        }
    }
}

@Composable
private fun AutoReportIncidentCard(
    incident: BatchIncident,
    onClick: () -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(300.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (incident.good && incident.checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                incident.previewUri?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = "Auto-report preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(incident.complaintName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${incident.plate} - ${incident.plateRegion}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBatchTime(incident.occurredAtIso), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (incident.address.isNotBlank()) {
                    Text(incident.address, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${incident.media.size} photo${if (incident.media.size == 1) "" else "s"} grouped",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onKeep
                ) {
                    Text("Keep", color = if (incident.good && incident.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDiscard
                ) {
                    Text("Discard", color = if (!incident.good || !incident.checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchIncidentSheet(
    incidents: List<BatchIncident>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIncidentChanged: (BatchIncident) -> Unit,
    onDelete: (BatchIncident) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { incidents.size })
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Review batch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            HorizontalPager(state = pagerState) { page ->
                BatchIncidentEditor(
                    incident = incidents[page],
                    onIncidentChanged = onIncidentChanged,
                    onDelete = onDelete
                )
            }
            Text("${pagerState.currentPage + 1} of ${incidents.size}", modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun BatchIncidentEditor(
    incident: BatchIncident,
    onIncidentChanged: (BatchIncident) -> Unit,
    onDelete: (BatchIncident) -> Unit
) {
    var plate by remember(incident.id, incident.plate) { mutableStateOf(incident.plate) }
    var state by remember(incident.id, incident.plateRegion) { mutableStateOf(incident.plateRegion) }
    var address by remember(incident.id, incident.address) { mutableStateOf(incident.address) }
    var complaintId by remember(incident.id, incident.complaintId) { mutableStateOf(incident.complaintId) }
    var showComplaints by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            incident.previewUri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Batch preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = Catalogs.complaintCategories.firstOrNull { it.id == complaintId }?.name ?: "Complaint",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showComplaints = true }
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (showComplaints) {
            Column {
                Catalogs.complaintCategories.forEach { complaint ->
                    Text(
                        text = complaint.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                complaintId = complaint.id
                                showComplaints = false
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(plate, { plate = it.uppercase().take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH) }, modifier = Modifier.weight(1f), label = { Text("Plate") }, singleLine = true)
            OutlinedTextField(state, { state = it.uppercase().take(2) }, modifier = Modifier.width(108.dp), label = { Text("State") }, singleLine = true)
        }
        OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Address") }, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "Discard",
                onClick = { onDelete(incident) },
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Save",
                onClick = {
                    onIncidentChanged(
                        incident.copy(
                            plate = plate.trim().uppercase(),
                            plateRegion = state.trim().uppercase(),
                            address = address.trim(),
                            complaintId = complaintId
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = plate.isNotBlank() && state.isNotBlank() && address.isNotBlank()
            )
        }
    }
}

private fun groupBatchAnalyses(analyses: List<BatchMediaAnalysis>): List<BatchIncident> {
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

private fun uniqueBatchAnalyses(analyses: List<BatchMediaAnalysis>): List<BatchMediaAnalysis> {
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

private fun shouldGroupAutoReportPhotos(
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

private const val AutoReportExactPlateGroupingWindowSeconds = 5 * 60L
private const val AutoReportSimilarPlateGroupingWindowSeconds = 2 * 60L
private const val AutoReportLocationRescueGroupingWindowSeconds = 60L
private const val AutoReportLocationRescueDistanceMeters = 45.0

private fun bestAutoReportAnalysis(group: List<BatchMediaAnalysis>): BatchMediaAnalysis? {
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

private fun autoReportHasCloseLocation(left: BatchMediaAnalysis, right: BatchMediaAnalysis): Boolean {
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

private fun usableAutoReportCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        abs(latitude) <= 90.0 &&
        abs(longitude) <= 180.0 &&
        (abs(latitude) > 0.000001 || abs(longitude) > 0.000001)

private fun autoReportDistanceMeters(
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

private fun normalizedAutoReportAddress(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()

private fun normalizedAutoReportPlate(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()

private fun normalizedPlateDistance(a: String, b: String): Int {
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

private fun BatchIncident.candidatesForMedia(mediaUri: String?): List<PlateCandidate> {
    if (mediaUri.isNullOrBlank()) return candidates
    return candidateGroups.firstOrNull { it.mediaUri == mediaUri }?.candidates?.takeIf { it.isNotEmpty() }
        ?: candidates
}

private fun BatchIncident.selectedAutoReportCandidate(mediaUri: String?): PlateCandidate? {
    val sourceCandidates = candidatesForMedia(mediaUri)
    return sourceCandidates.firstOrNull { normalizedAutoReportPlate(it.plate) == normalizedAutoReportPlate(plate) }
        ?: sourceCandidates.firstOrNull()
        ?: candidates.firstOrNull()
}

private fun BatchIncident.withAutoReportCandidate(candidate: PlateCandidate): BatchIncident =
    copy(
        plate = candidate.plate.uppercase().take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH),
        plateRegion = candidate.state?.uppercase()?.take(2) ?: plateRegion
    )

private fun BatchIncident.toComposerUiState(
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

private fun List<PlateCandidate>.autoReportDetectionSourceSize(): Size? =
    firstNotNullOfOrNull { candidate ->
        val width = candidate.sourceImageWidth?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
        val height = candidate.sourceImageHeight?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
        Size(width.toFloat(), height.toFloat())
    }

private fun autoReportPlatePolygon(
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

private fun PlateCandidate.autoReportHasUsableCornerPoints(): Boolean {
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

private fun List<Offset>.autoReportInflateFromCentroid(amount: Float): List<Offset> {
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

private fun List<Offset>.autoReportContainsPoint(point: Offset): Boolean {
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

private fun BatchIncident.toQueuedReport(): BatchQueuedReport =
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

private data class AutoReportBatchResult(
    val totalPhotos: Int,
    val incidents: List<BatchIncident>,
    val processedPhotos: List<AutoReportProcessedPhoto>
)

private data class AutoReportProcessedPhoto(
    val id: String,
    val previewUri: String,
    val resultTitle: String,
    val resultDetail: String,
    val matched: Boolean
)

private data class AutoReportMediaItem(
    val media: SubmissionMedia,
    val sortTime: Long
)

private enum class AutoReportScanWindow(
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

private suspend fun scanAutoReportPhotos(
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

private fun queryAutoReportImages(context: Context, since: Instant): List<AutoReportMediaItem> {
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

private fun mediaContentHash(context: Context, media: SubmissionMedia): String? =
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

private fun List<PlateCandidate>.bestAutoReportCandidate(context: Context): PlateCandidate? =
    filter { candidate ->
        val requiredPlateConfidence = candidate.autoReportPlateThreshold(context)
        candidate.confidence >= requiredPlateConfidence &&
            (candidate.stateConfidence ?: 0f) >= AutoReportThresholds.stateConfidence(context) &&
            !candidate.state.isNullOrBlank()
    }.maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

private fun List<PlateCandidate>.bestObservedAutoReportCandidate(): PlateCandidate? =
    maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

private fun PlateCandidate.autoReportPlateThreshold(context: Context): Float =
    if (state == "NY" && plateType in setOf("TAXI", "TLC")) {
        AutoReportThresholds.postInferencePlateConfidence(context)
    } else {
        AutoReportThresholds.plateConfidence(context)
    }

private fun PlateCandidate.autoReportConfidenceSummary(context: Context): String =
    "${plate} ${state.orEmpty()} plate ${AutoReportThresholds.percent(confidence)} (needs ${AutoReportThresholds.percent(autoReportPlateThreshold(context))}), state ${AutoReportThresholds.percent(stateConfidence ?: 0f)} (needs ${AutoReportThresholds.percent(AutoReportThresholds.stateConfidence(context))})"

private fun ComplaintInferenceResult?.acceptedAutoReportComplaint(context: Context): Boolean =
    this != null &&
        accepted &&
        confidence >= AutoReportThresholds.complaintConfidence(context) &&
        normalizedBatchComplaintId(complaintId) in autoReportComplaintIds

private fun ComplaintInferenceResult?.autoReportComplaintConfidenceSummary(context: Context, matched: Boolean): String {
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

private fun normalizedBatchComplaintId(complaintId: String): String =
    when (complaintId) {
        "blocked_bike_lane" -> "Z8vjWz8uYr"
        "blocked_crosswalk" -> "GzRxlMN1vl"
        else -> complaintId
    }

private fun batchComplaintLabel(complaintId: String): String =
    Catalogs.complaintCategories.firstOrNull { it.id == normalizedBatchComplaintId(complaintId) }?.name
        ?: when (normalizedBatchComplaintId(complaintId)) {
            "Z8vjWz8uYr" -> "Blocked bike lane"
            "GzRxlMN1vl" -> "Blocked crosswalk"
            else -> "Complaint"
        }

private fun BatchIncident.autoReportAiSummary(): String {
    val photoText = "${media.size} photo${if (media.size == 1) "" else "s"}"
    val plateScore = candidates.maxOfOrNull { it.confidence }?.let(AutoReportThresholds::percent) ?: "unknown"
    val stateScore = candidates.mapNotNull { it.stateConfidence }.maxOrNull()?.let(AutoReportThresholds::percent) ?: "unknown"
    val complaintScore = complaintConfidence?.let(AutoReportThresholds::percent) ?: "unknown"
    return "Reported AI grouped $photoText that appear to show ${complaintName.lowercase()} involving plate $plate in $plateRegion. Plate confidence $plateScore, state confidence $stateScore, infraction confidence $complaintScore."
}

private val autoReportComplaintIds = setOf("Z8vjWz8uYr", "GzRxlMN1vl")

private fun parseInstantOrNull(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()

private fun formatBatchTime(value: String): String =
    parseInstantOrNull(value)
        ?.let { DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(it) }
        ?: value
