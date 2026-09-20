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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
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
fun AutoReportTutorialCard(
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
fun AutoReportScanWindowSpinner(
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
fun BatchSummaryCard(
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
fun BatchIncidentRow(
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
fun AutoReportProcessedPhotoRow(photo: AutoReportProcessedPhoto) {
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
fun AutoReportScanProgressSheet(
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
fun AutoReportReviewSheet(
    incidents: List<BatchIncident>,
    processedPhotos: List<AutoReportProcessedPhoto>,
    onDismiss: () -> Unit,
    onIncidentChanged: (BatchIncident) -> Unit,
    onKeepChange: (BatchIncident, Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val pageCount = incidents.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
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
fun AutoReportReviewCarouselPage(
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
fun AutoReportReviewActions(
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

fun autoReportKeptCountAfter(
    incidents: List<BatchIncident>,
    incidentId: String,
    keep: Boolean
): Int = incidents.count { incident ->
    if (incident.id == incidentId) keep else incident.checked && incident.good
}

@Composable
fun AutoReportReviewPageIndicator(
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
