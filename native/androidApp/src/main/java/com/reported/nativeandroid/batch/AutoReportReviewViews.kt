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
fun AutoReportIncidentReviewCard(
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
fun AutoReportMainPhotoPreview(
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
fun AutoReportFullScreenPhotoViewer(
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
fun AutoReportPlateBoundsOverlay(
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
fun AutoReportSelectionField(
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
fun AutoReportPlateCandidateStrip(
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
fun AutoReportIncidentPhotoStrip(incident: BatchIncident) {
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
fun AutoReportReviewField(title: String, value: String) {
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
fun AutoReportSubmissionSummaryCard(
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
