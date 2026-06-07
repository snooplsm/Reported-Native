package com.reported.nativeandroid.batch

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.reported.nativeandroid.screens.NativeAlprEngine
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.nativeandroid.screens.SecondaryButton
import com.reported.nativeandroid.screens.buildSubmissionMedia
import com.reported.nativeandroid.screens.extractSubmissionMetadata
import com.reported.nativeandroid.screens.reverseGeocodeAddress
import com.reported.shared.model.Catalogs
import com.reported.shared.model.PlatePatternClassifier
import kotlinx.coroutines.launch
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
    val checked: Boolean = true,
    val good: Boolean = true
) {
    val complaintName: String
        get() = Catalogs.complaintCategories.firstOrNull { it.id == complaintId }?.name ?: "Complaint"

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
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incidents = remember { mutableStateListOf<BatchIncident>() }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var deleteCandidate by remember { mutableStateOf<BatchIncident?>(null) }
    var processing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

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
            title = { Text("Batch") },
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
                    text = "Batch",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                    Text("Select")
                }
            }

            if (processing) {
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

            BatchSummaryCard(incidents = incidents)

            if (incidents.isNotEmpty()) {
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

            val submitCount = incidents.count { it.checked && it.good }
            PrimaryButton(
                text = "Submit $submitCount Report${if (submitCount == 1) "" else "s"}",
                enabled = submitCount > 0 && !processing,
                onClick = {
                    if (!isAuthorized) {
                        onRequireLogin()
                    } else {
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
                        message = "Submitting ${toSubmit.size} batch report${if (toSubmit.size == 1) "" else "s"} in the background."
                    }
                }
            )
        }
    }
}

@Composable
private fun BatchSummaryCard(incidents: List<BatchIncident>) {
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
                Text("Select photos to build a batch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val sorted = analyses
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
            existingCandidate?.plate == candidate.plate &&
                (existingCandidate.state ?: existing.region ?: "NY") == (candidate.state ?: analysis.region ?: "NY") &&
                instant != null &&
                existingInstant != null &&
                abs(instant.epochSecond - existingInstant.epochSecond) <= 30L
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
            complaintId = group.firstNotNullOfOrNull { it.complaintId } ?: Catalogs.complaintCategories.first().id,
            occurredAtIso = occurredAt,
            address = group.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
            latitude = group.firstNotNullOfOrNull { it.latitude },
            longitude = group.firstNotNullOfOrNull { it.longitude },
            media = group.map { it.media }.take(3),
            candidates = group.flatMap { it.candidates }.sortedByDescending { it.confidence }
        )
    }
}

private fun BatchIncident.toQueuedReport(): BatchQueuedReport =
    BatchQueuedReport(
        id = id,
        plate = plate,
        plateRegion = plateRegion,
        address = address,
        complaintId = complaintId,
        occurredAtIso = occurredAtIso,
        latitude = latitude,
        longitude = longitude,
        media = media
    )

private fun parseInstantOrNull(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()

private fun formatBatchTime(value: String): String =
    parseInstantOrNull(value)
        ?.let { DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(java.time.ZoneId.systemDefault()).format(it) }
        ?: value
