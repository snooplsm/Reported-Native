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
fun AutoReportIncidentCarousel(
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
fun AutoReportIncidentCard(
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
fun BatchIncidentSheet(
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
fun BatchIncidentEditor(
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
