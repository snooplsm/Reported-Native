package com.reported.nativeandroid.batch

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.batch.BatchQueuedReport
import com.reported.nativeandroid.batch.BatchSubmitStore
import com.reported.nativeandroid.batch.SubmitBatchReportWorker
import com.reported.nativeandroid.media.AutoReportThresholds
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.screens.ComplaintInferenceResult
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.SecondaryButton
import com.reported.nativeandroid.screens.buildSubmissionMedia
import com.reported.nativeandroid.screens.extractSubmissionMetadata
import com.reported.nativeandroid.screens.reverseGeocodeAddress
import com.reported.shared.model.Catalogs
import com.reported.shared.model.PlatePatternClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

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

private data class BatchIncident(
    val id: String = UUID.randomUUID().toString(),
    val plate: String,
    val plateRegion: String,
    val complaintId: String,
    val occurredAtIso: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val media: List<SubmissionMedia>,
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
    val scope = rememberCoroutineScope()
    val incidents = remember { mutableStateListOf<BatchIncident>() }
    val processedPhotos = remember { mutableStateListOf<AutoReportProcessedPhoto>() }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var deleteCandidate by remember { mutableStateOf<BatchIncident?>(null) }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var autoReportWindow by remember { mutableStateOf(AutoReportScanWindow.SevenDays) }
    var autoReportScanJob by remember { mutableStateOf<Job?>(null) }
    var showAutoReportReview by remember { mutableStateOf(false) }

    fun launchAutoReportScan() {
        autoReportScanJob?.cancel()
        autoReportScanJob = scope.launch {
            processing = true
            progress = 0f
            status = "Scanning ${autoReportWindow.sentenceLabel}"
            processedPhotos.clear()
            incidents.clear()
            showAutoReportReview = false
            try {
                val result = scanAutoReportPhotos(
                    context = context,
                    window = autoReportWindow,
                    onProgress = { nextStatus, nextProgress ->
                        status = nextStatus
                        progress = nextProgress
                    }
                )
                incidents.clear()
                incidents.addAll(result.incidents)
                processedPhotos.clear()
                processedPhotos.addAll(result.processedPhotos)
                status = "Found ${incidents.size} possible report${if (incidents.size == 1) "" else "s"}"
                if (result.totalPhotos == 0) {
                    message = "No photos from ${autoReportWindow.sentenceLabel} were available to scan."
                } else {
                    showAutoReportReview = true
                    message = null
                }
            } catch (_: CancellationException) {
                message = "Photo scan cancelled."
            } finally {
                processing = false
                autoReportScanJob = null
            }
        }
    }

    val autoReportPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaScannerSettings.hasAutoReportPermissions(context)) {
            launchAutoReportScan()
        } else {
            message = "Photo library permission is needed to scan ${autoReportWindow.sentenceLabel} without opening the gallery."
        }
    }

    fun submitKeptReports() {
        if (!isAuthorized) {
            onRequireLogin()
            return
        }
        val toSubmit = incidents.filter { it.checked && it.good }
        toSubmit.forEach { incident ->
            val report = incident.toQueuedReport()
            BatchSubmitStore.save(context, report)
            val request = OneTimeWorkRequestBuilder<SubmitBatchReportWorker>()
                .setInputData(workDataOf(SubmitBatchReportWorker.KEY_REPORT_ID to report.id))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
        incidents.removeAll(toSubmit.toSet())
        showAutoReportReview = false
        message = "Submitting ${toSubmit.size} batch report${if (toSubmit.size == 1) "" else "s"} in the background."
    }

    fun persistDocumentRead(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun isVideoUri(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        if (type.startsWith("video/")) return true
        if (type.startsWith("image/")) return false
        val path = uri.toString().substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp") || path.endsWith(".webm")
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            processing = true
            progress = 0f
            status = "Preparing media"
            val analyses = mutableListOf<BatchMediaAnalysis>()
            uris.forEachIndexed { index, uri ->
                persistDocumentRead(uri)
                val media = buildSubmissionMedia(context, uri, isVideoUri(uri))
                status = "Reading media ${index + 1} of ${uris.size}"
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
                progress = (index + 1).toFloat() / uris.size.toFloat()
            }
            incidents.clear()
            incidents.addAll(groupBatchAnalyses(analyses))
            status = "Found ${incidents.size} possible report${if (incidents.size == 1) "" else "s"}"
            processing = false
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK")
                }
            },
            title = { Text(title) },
            text = { Text(it) }
        )
    }

    deleteCandidate?.let { incident ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete batch report?") },
            text = { Text("${incident.plate} - ${incident.plateRegion} will be removed from this batch.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        incidents.removeAll { it.id == incident.id }
                        deleteCandidate = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            }
        )
    }

    selectedIndex?.let { index ->
        if (incidents.isNotEmpty()) {
            BatchIncidentSheet(
                incidents = incidents,
                initialIndex = index.coerceIn(0, incidents.lastIndex),
                onDismiss = { selectedIndex = null },
                onIncidentChanged = { changed ->
                    val itemIndex = incidents.indexOfFirst { it.id == changed.id }
                    if (itemIndex >= 0) incidents[itemIndex] = changed
                },
                onDelete = { deleted ->
                    incidents.removeAll { it.id == deleted.id }
                    selectedIndex = null
                }
            )
        }
    }

    if (autoReportMode && processing) {
        AutoReportScanProgressSheet(
            status = status.ifBlank { "Preparing photo scan" },
            progress = progress,
            onCancel = { autoReportScanJob?.cancel() }
        )
    }

    if (autoReportMode && showAutoReportReview) {
        AutoReportReviewSheet(
            incidents = incidents,
            processedPhotos = processedPhotos,
            onDismiss = { showAutoReportReview = false },
            onKeepChange = { incident, keep ->
                val itemIndex = incidents.indexOfFirst { it.id == incident.id }
                if (itemIndex >= 0) {
                    incidents[itemIndex] = incidents[itemIndex].copy(
                        good = keep,
                        checked = keep
                    )
                }
            },
            onSubmit = ::submitKeptReports
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
                            if (MediaScannerSettings.hasAutoReportPermissions(context)) {
                                launchAutoReportScan()
                            } else {
                                autoReportPermissionLauncher.launch(MediaScannerSettings.autoReportPermissions())
                            }
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
                    onScanWindowSelected = { autoReportWindow = it },
                    onScan = {
                        if (MediaScannerSettings.hasAutoReportPermissions(context)) {
                            launchAutoReportScan()
                        } else {
                            autoReportPermissionLauncher.launch(MediaScannerSettings.autoReportPermissions())
                        }
                    }
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
                    onClick = { showAutoReportReview = true },
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
                                selectedIndex = incidents.indexOfFirst { it.id == incident.id }
                            },
                            onLongPress = { deleteCandidate = incident },
                            onCheckedChange = { checked ->
                                val itemIndex = incidents.indexOfFirst { it.id == incident.id }
                                if (itemIndex >= 0) incidents[itemIndex] = incidents[itemIndex].copy(checked = checked)
                            },
                            onGoodChange = { good ->
                                val itemIndex = incidents.indexOfFirst { it.id == incident.id }
                                if (itemIndex >= 0) incidents[itemIndex] = incidents[itemIndex].copy(good = good)
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
                    onClick = ::submitKeptReports
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
    var expanded by remember { mutableStateOf(false) }
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
                Box(modifier = Modifier.weight(0.8f)) {
                    TextButton(
                        enabled = enabled,
                        onClick = { expanded = true }
                    ) {
                        Text(scanWindow.label)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AutoReportScanWindow.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onScanWindowSelected(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                PrimaryButton(
                    text = "Scan Photos",
                    enabled = enabled,
                    onClick = onScan,
                    modifier = Modifier.weight(1.6f)
                )
            }
        }
    }
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
    onKeepChange: (BatchIncident, Boolean) -> Unit,
    onSubmit: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { incidents.size + 1 })
    val submitCount = incidents.count { it.checked && it.good }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Review Auto-Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "$submitCount kept, ${maxOf(0, incidents.size - submitCount)} discarded",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
            ) { page ->
                if (page < incidents.size) {
                    val incident = incidents[page]
                    AutoReportReviewCard(
                        incident = incident,
                        onKeep = { onKeepChange(incident, true) },
                        onDiscard = { onKeepChange(incident, false) }
                    )
                } else {
                    AutoReportSubmissionSummaryCard(
                        incidents = incidents,
                        processedPhotos = processedPhotos,
                        onSubmit = onSubmit
                    )
                }
            }
            Text(
                "${pagerState.currentPage + 1} of ${incidents.size + 1}",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AutoReportReviewCard(
    incident: BatchIncident,
    onKeep: () -> Unit,
    onDiscard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = if (incident.good && incident.checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
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
            Text(incident.complaintName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            AutoReportReviewField("License Plate", incident.plate)
            AutoReportReviewField("State", incident.plateRegion)
            AutoReportReviewField("Complaint", incident.complaintName)
            AutoReportReviewField("Incident Time", formatBatchTime(incident.occurredAtIso))
            AutoReportReviewField("Address", incident.address.ifBlank { "No location found" })
            AutoReportReviewField(
                "Photos",
                "${incident.media.size} photo${if (incident.media.size == 1) "" else "s"} grouped for this report"
            )
            AutoReportReviewField("AI Summary", incident.autoReportAiSummary())
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
    onSubmit: () -> Unit
) {
    val kept = incidents.filter { it.checked && it.good }
    val keptPhotos = kept.sumOf { it.media.size }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
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
            if (kept.isEmpty()) {
                Text("No reports are currently marked Keep.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                kept.forEach { incident ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(incident.complaintName, fontWeight = FontWeight.Bold)
                            Text(
                                "${incident.plate} - ${incident.plateRegion} • ${formatBatchTime(incident.occurredAtIso)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
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
            if (processedPhotos.isNotEmpty()) {
                Text("Processed photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                processedPhotos.forEach { photo ->
                    AutoReportProcessedPhotoRow(photo = photo)
                }
            }
            PrimaryButton(
                text = "Submit ${kept.size} Report${if (kept.size == 1) "" else "s"}",
                enabled = kept.isNotEmpty(),
                onClick = onSubmit
            )
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
        val best = group.flatMap { it.candidates }.maxByOrNull { it.confidence } ?: return@mapNotNull null
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
    val similarPlate = plateDistance <= if (minOf(existingCandidate.plate.length, nextCandidate.plate.length) >= 6) 2 else 1
    if (!similarPlate) return false

    val gapSeconds = abs(nextInstant.epochSecond - existingInstant.epochSecond)
    val windowSeconds = if (plateDistance == 0) 5 * 60L else 2 * 60L
    return gapSeconds <= windowSeconds
}

private fun normalizedPlateDistance(a: String, b: String): Int {
    val left = a.filter(Char::isLetterOrDigit).uppercase()
    val right = b.filter(Char::isLetterOrDigit).uppercase()
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

private fun BatchIncident.toQueuedReport(): BatchQueuedReport =
    BatchQueuedReport(
        id = id,
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        complaintId = normalizedBatchComplaintId(complaintId),
        occurredAtIso = occurredAtIso,
        latitude = latitude,
        longitude = longitude,
        media = media
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
        val candidates = NativeAlprEngine.detectLicensePlates(context, media)
        val bestPlate = candidates.bestAutoReportCandidate()
        if (bestPlate == null) {
            val topCandidate = candidates.bestObservedAutoReportCandidate()
            processedPhotos += AutoReportProcessedPhoto(
                id = media.uri,
                previewUri = media.uri,
                resultTitle = if (candidates.isEmpty()) "No vehicle plate found" else "No qualified vehicle plate",
                resultDetail = if (candidates.isEmpty()) {
                    "Plate AI found no readable plate candidates."
                } else if (topCandidate != null) {
                    "Top plate AI: ${topCandidate.autoReportConfidenceSummary()}."
                } else {
                    "Plate AI found ${candidates.size} candidate${if (candidates.size == 1) "" else "s"}, but none met the threshold."
                },
                matched = false
            )
            return@forEachIndexed
        }
        val complaintInference = NativeAlprEngine.inferComplaint(context, media)
        val complaintId = normalizedBatchComplaintId(complaintInference?.complaintId.orEmpty())
        if (!complaintInference.acceptedAutoReportComplaint()) {
            processedPhotos += AutoReportProcessedPhoto(
                id = media.uri,
                previewUri = media.uri,
                resultTitle = "Not report-worthy",
                resultDetail = "Plate AI passed: ${bestPlate.autoReportConfidenceSummary()}. ${complaintInference.autoReportComplaintConfidenceSummary(matched = false)}",
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
            contentHash = mediaContentHash(context, media),
            candidates = orderedCandidates
        )
        processedPhotos += AutoReportProcessedPhoto(
            id = media.uri,
            previewUri = media.uri,
            resultTitle = "Matched ${batchComplaintLabel(complaintId)}",
            resultDetail = "Plate AI passed: ${bestPlate.autoReportConfidenceSummary()}. ${complaintInference.autoReportComplaintConfidenceSummary(matched = true)}",
            matched = true
        )
    }

    onProgress("Grouping possible reports", 1f)
    return AutoReportBatchResult(
        totalPhotos = mediaItems.size,
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

private fun List<PlateCandidate>.bestAutoReportCandidate(): PlateCandidate? =
    filter { candidate ->
        val requiredPlateConfidence = candidate.autoReportPlateThreshold()
        candidate.confidence >= requiredPlateConfidence &&
            (candidate.stateConfidence ?: 0f) >= AutoReportThresholds.STATE_CONFIDENCE &&
            !candidate.state.isNullOrBlank()
    }.maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

private fun List<PlateCandidate>.bestObservedAutoReportCandidate(): PlateCandidate? =
    maxByOrNull { candidate ->
        candidate.confidence + (candidate.stateConfidence ?: 0f)
    }

private fun PlateCandidate.autoReportPlateThreshold(): Float =
    if (state == "NY" && plateType in setOf("TAXI", "TLC")) {
        AutoReportThresholds.POST_INFERENCE_PLATE_CONFIDENCE
    } else {
        AutoReportThresholds.PLATE_CONFIDENCE
    }

private fun PlateCandidate.autoReportConfidenceSummary(): String =
    "${plate} ${state.orEmpty()} plate ${AutoReportThresholds.percent(confidence)} (needs ${AutoReportThresholds.percent(autoReportPlateThreshold())}), state ${AutoReportThresholds.percent(stateConfidence ?: 0f)} (needs ${AutoReportThresholds.percent(AutoReportThresholds.STATE_CONFIDENCE)})"

private fun ComplaintInferenceResult?.acceptedAutoReportComplaint(): Boolean =
    this != null && accepted && normalizedBatchComplaintId(complaintId) in autoReportComplaintIds

private fun ComplaintInferenceResult?.autoReportComplaintConfidenceSummary(matched: Boolean): String {
    val required = AutoReportThresholds.percent(AutoReportThresholds.COMPLAINT_CONFIDENCE)
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
