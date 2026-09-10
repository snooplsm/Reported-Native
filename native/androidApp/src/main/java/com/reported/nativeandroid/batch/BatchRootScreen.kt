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
