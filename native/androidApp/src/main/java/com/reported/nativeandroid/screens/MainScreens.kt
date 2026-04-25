package com.reported.nativeandroid.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.SvgDecoder
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.reported.nativeandroid.app.ComposerAction
import com.reported.nativeandroid.app.ComposerUiState
import com.reported.nativeandroid.app.ComposerViewModel
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.app.ReportsMode
import com.reported.nativeandroid.app.ReportsViewModel
import com.reported.nativeandroid.app.SharedMediaRequest
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.SubmissionStage
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.ReportSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val ROTATED_PLATE_OVERLAY_THRESHOLD_DEGREES = 4f

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportComposerScreen(
    isAuthorized: Boolean,
    onRequireLogin: ((() -> Unit)?) -> Unit,
    onOpenMenu: () -> Unit,
    sharedMediaRequest: SharedMediaRequest? = null,
    onSharedMediaConsumed: (Long) -> Unit = {},
    vm: ComposerViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val verifyListState = rememberLazyListState()
    var isLandscapeLayout by remember { mutableStateOf(false) }
    val complaintOptions = remember {
        listOf(
            ComplaintOption("blocked_bike_lane", "Blocked bike lane", "file:///android_asset/complaints/bikelane.svg"),
            ComplaintOption("blocked_crosswalk", "Blocked crosswalk", "file:///android_asset/complaints/crosswalk.svg"),
            ComplaintOption("ran_red_light", "Ran red light", "file:///android_asset/complaints/ranredlight.jpg", "complaints/ranredlight.json"),
            ComplaintOption("drove_recklessly", "Drove recklessly", "file:///android_asset/complaints/reckless.png", "complaints/reckless.json"),
            ComplaintOption("parked_illegally", "Parked illegally", "file:///android_asset/complaints/parkedillegally.jpg", "complaints/parkedillegally.json")
        )
    }
    val animatedComplaintIndices = remember(complaintOptions) {
        complaintOptions.mapIndexedNotNull { index, option -> index.takeIf { option.lottieAssetPath != null } }
    }
    var activeAnimatedOptionIndex by remember(animatedComplaintIndices) {
        mutableIntStateOf(animatedComplaintIndices.firstOrNull() ?: -1)
    }
    var pendingComplaintId by remember { mutableStateOf<String?>(null) }
    var pendingMultipleSelection by remember { mutableStateOf(false) }
    var pendingMediaForCurrentReport by remember { mutableStateOf(false) }
    var showComplaintChooser by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAddressMap by remember { mutableStateOf(false) }
    var showPlateCandidates by remember { mutableStateOf(false) }
    var pendingPlateCandidate by remember { mutableStateOf<PlateCandidate?>(null) }
    var detectionProgressMinimized by remember { mutableStateOf(false) }
    var metadataJob by remember { mutableStateOf<Job?>(null) }
    var videoScanJob by remember { mutableStateOf<Job?>(null) }
    var videoScanPaused by remember { mutableStateOf(false) }
    var videoPreviewScrubMs by remember { mutableLongStateOf(0L) }
    var addressSearchJob by remember { mutableStateOf<Job?>(null) }
    var mapLookupJob by remember { mutableStateOf<Job?>(null) }
    val hasDraftContent = remember(state) {
        state.stage != SubmissionStage.PICK_MEDIA ||
            state.selectedComplaintId != null ||
            state.primaryMedia != null ||
            state.extraMedia.isNotEmpty() ||
            state.pendingMediaSelection != null ||
            state.plate.isNotBlank() ||
            state.plateRegion != "NY" ||
            state.addressQuery.isNotBlank() ||
            state.description.isNotBlank() ||
            state.notes.isNotBlank() ||
            state.occurredAtIso.isNotBlank() ||
            state.latitude != null ||
            state.longitude != null ||
            state.detectingPlates ||
            state.plateCandidates.isNotEmpty()
    }

    LaunchedEffect(animatedComplaintIndices) {
        if (animatedComplaintIndices.isEmpty()) return@LaunchedEffect
        var pointer = 0
        while (true) {
            activeAnimatedOptionIndex = animatedComplaintIndices[pointer]
            delay(1800)
            pointer = (pointer + 1) % animatedComplaintIndices.size
        }
    }

    LaunchedEffect(state.validationErrors) {
        if (!state.validationErrors.hasErrors) return@LaunchedEffect
        if (state.validationErrors.media != null) {
            verifyListState.animateScrollToItem(0)
        } else {
            verifyListState.animateScrollToItem(0, 180)
        }
    }

    LaunchedEffect(state.detectingPlates) {
        detectionProgressMinimized = false
        if (!state.detectingPlates) {
            videoScanPaused = false
        }
    }

    LaunchedEffect(state.detectionFrameTimeMs, videoScanPaused) {
        if (!videoScanPaused) {
            videoPreviewScrubMs = state.detectionFrameTimeMs
        }
    }

    fun isVideoUri(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        if (type.startsWith("video/")) return true
        if (type.startsWith("image/")) return false
        val path = uri.toString().substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp") || path.endsWith(".webm")
    }

    fun handleChosenMedia(uri: Uri, complaintId: String?, forCurrentReport: Boolean) {
        val isVideo = isVideoUri(uri)
        scope.launch {
            val media = buildSubmissionMedia(context, uri, isVideo)
            if (forCurrentReport) {
                vm.onAction(ComposerAction.PrimaryMediaChosenForCurrentReport(media))
            } else if (complaintId != null) {
                vm.onAction(ComposerAction.PrimaryMediaChosen(media, complaintId))
            } else {
                vm.onAction(ComposerAction.UploadMediaChosen(media))
            }
            metadataJob?.cancel()
            metadataJob = launch {
                vm.onAction(ComposerAction.DetectionProgressChanged("Reading media metadata", 0.1f))
                val metadata = extractSubmissionMetadata(context, media)
                val inferredState = if (metadata.latitude != null && metadata.longitude != null) "NY" else null
                val inferredAddress = if (metadata.latitude != null && metadata.longitude != null) {
                    vm.onAction(ComposerAction.DetectionProgressChanged("Finding NYC address", 0.35f))
                    reverseGeocodeNyc(metadata.latitude, metadata.longitude)?.label
                } else null
                vm.onAction(ComposerAction.MetadataApplied(
                    occurredAtIso = metadata.occurredAtIso,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    inferredState = inferredState,
                    inferredAddress = inferredAddress
                ))
                if (!media.isVideo) {
                    vm.onAction(ComposerAction.DetectionProgressChanged("Detecting plates", 0.65f))
                    val candidates = NativeAlprEngine.detectLicensePlates(context, media)
                    val topCandidate = candidates.firstOrNull()
                    vm.onAction(ComposerAction.DetectionFinished(
                        candidates = candidates,
                        inferredPlate = topCandidate?.plate,
                        inferredState = inferredState
                    ))
                }
            }
        }
    }

    fun addExtraMedia(uri: Uri) {
        val isVideo = isVideoUri(uri)
        scope.launch {
            val media = buildSubmissionMedia(context, uri, isVideo)
            vm.onAction(ComposerAction.ExtraMediaAdded(media))
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleChosenMedia(it, pendingComplaintId, pendingMediaForCurrentReport) }
        pendingComplaintId = null
        pendingMediaForCurrentReport = false
        pendingMultipleSelection = false
    }

    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val isVideo = isVideoUri(uri)
                val media = buildSubmissionMedia(context, uri, isVideo)
                vm.onAction(ComposerAction.ExtraMediaAdded(media))
            }
        }
        pendingMediaForCurrentReport = false
        pendingMultipleSelection = false
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        if (pendingMultipleSelection) {
            multiMediaPicker.launch(request)
        } else {
            mediaPicker.launch(request)
        }
    }

    LaunchedEffect(Unit) { vm.onAction(ComposerAction.LoadDraft) }

    LaunchedEffect(sharedMediaRequest?.id, state.draftLoaded) {
        val request = sharedMediaRequest ?: return@LaunchedEffect
        if (!state.draftLoaded) return@LaunchedEffect

        val uris = request.uris
        if (uris.isEmpty()) {
            onSharedMediaConsumed(request.id)
            return@LaunchedEffect
        }

        if (state.primaryMedia != null) {
            uris.forEach(::addExtraMedia)
        } else {
            handleChosenMedia(
                uri = uris.first(),
                complaintId = state.selectedComplaintId,
                forCurrentReport = true
            )
            uris.drop(1).forEach(::addExtraMedia)
        }
        onSharedMediaConsumed(request.id)
    }

    LaunchedEffect(state.addressQuery) {
        if (state.stage != SubmissionStage.VERIFY || state.addressQuery.isBlank() || state.addressQuery == state.address) {
            vm.onAction(ComposerAction.AddressSuggestionsChanged(emptyList()))
            return@LaunchedEffect
        }
        addressSearchJob?.cancel()
        addressSearchJob = scope.launch {
            vm.onAction(ComposerAction.AddressLookupLoadingChanged(true))
            delay(250)
            vm.onAction(ComposerAction.AddressSuggestionsChanged(searchNycAddresses(state.addressQuery)))
        }
    }

    LaunchedEffect(state.stage, state.primaryMedia?.uri.toString()) {
        if (state.stage == SubmissionStage.VERIFY && state.primaryMedia != null) {
            verifyListState.animateScrollToItem(0)
        }
    }

    if (state.complaintSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {},
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("What kind of complaint is this?", style = MaterialTheme.typography.titleLarge)
                complaintOptions.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { option ->
                            ComplaintTile(
                                option = option,
                                modifier = Modifier.weight(1f),
                                animate = complaintOptions.indexOfFirst { it.id == option.id } == activeAnimatedOptionIndex,
                                showImage = BuildConfig.SHOW_COMPLAINT_IMAGES,
                                onClick = { vm.onAction(ComposerAction.PendingComplaintConfirmed(option.id)) }
                            )
                        }
                        if (row.size == 1) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    val pendingVideoMedia = state.pendingVideoProcessingMedia?.takeIf { it.isVideo }
    if (state.awaitingVideoProcessingDecision && pendingVideoMedia != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Process video for license plates?") },
            text = { Text("We can scan every video frame for candidate license plates, then let you pick the best one.") },
            confirmButton = {
                TextButton(onClick = {
                    val videoMedia = pendingVideoMedia
                    videoScanPaused = false
                    vm.onAction(ComposerAction.VideoProcessingDecision(true))
                    videoScanJob?.cancel()
                    videoScanJob = scope.launch {
                        val candidates = NativeAlprEngine.detectLicensePlatesInVideo(context, videoMedia) { progress ->
                            val totalFrames = progress.totalFrames.coerceAtLeast(1)
                            val percent = progress.processedFrames.toFloat() / totalFrames.toFloat()
                            val foundText = when (progress.candidatesFound) {
                                0 -> "no plates yet"
                                1 -> "1 possible plate"
                                else -> "${progress.candidatesFound} possible plates"
                            }
                            vm.onAction(
                                ComposerAction.DetectionProgressChanged(
                                    message = "Scanning frame ${progress.processedFrames}/$totalFrames, $foundText",
                                    progress = percent,
                                    framePreviewUri = progress.framePreviewUri,
                                    frameTimeMs = progress.frameTimeUs / 1_000L,
                                    videoDurationMs = progress.durationMs,
                                    frameCandidates = progress.frameCandidates,
                                    allCandidates = progress.allCandidates
                                )
                            )
                            while (videoScanPaused) {
                                delay(80)
                            }
                        }
                        val topCandidate = candidates.firstOrNull()
                        vm.onAction(
                            ComposerAction.DetectionFinished(
                                candidates = candidates,
                                inferredPlate = topCandidate?.plate,
                                inferredState = topCandidate?.state
                            )
                        )
                    }
                }) { Text("Process") }
            },
            dismissButton = {
                TextButton(onClick = { vm.onAction(ComposerAction.VideoProcessingDecision(false)) }) { Text("Skip") }
            }
        )
    }

    if (showComplaintChooser) {
        AlertDialog(
            onDismissRequest = { showComplaintChooser = false },
            title = { Text("Change complaint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    complaintOptions.forEach { option ->
                        Text(
                            text = option.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.onAction(ComposerAction.SelectedComplaintChanged(option.id))
                                    showComplaintChooser = false
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showComplaintChooser = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard report?") },
            text = { Text("This will clear the current report and remove any saved draft for it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        vm.onAction(ComposerAction.DiscardDraftConfirmed)
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddressMap) {
        AddressMapSheet(
            initialLatLng = LatLng(state.latitude ?: 40.7128, state.longitude ?: -74.0060),
            initialAddress = state.addressQuery.ifBlank { state.address },
            onDismiss = { showAddressMap = false },
            onLocationSettled = { suggestion ->
                vm.onAction(ComposerAction.AddressChosen(suggestion))
            }
        )
    }

    if (showPlateCandidates) {
        PlateCandidatePickerDialog(
            candidates = state.plateCandidates,
            selectedPlate = state.selectedPlateCandidate,
            onDismiss = { showPlateCandidates = false },
            onSelected = { candidate ->
                vm.onAction(ComposerAction.PlateCandidateChosen(candidate))
                showPlateCandidates = false
            }
        )
    }

    pendingPlateCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingPlateCandidate = null },
            title = { Text("Use this plate?") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PlateCandidateThumbnail(candidate = candidate)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(candidate.plate, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${(candidate.confidence * 100).toInt()}% confidence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        candidate.stateClassifierText()?.let { classifierText ->
                            Text(
                                classifierText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        candidate.plateTypeText()?.let { typeText ->
                            Text(
                                typeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.onAction(ComposerAction.PlateCandidateChosen(candidate))
                        pendingPlateCandidate = null
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlateCandidate = null }) {
                    Text("No")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val landscapeVerifyChrome = isLandscapeLayout && state.stage == SubmissionStage.VERIFY
        if (!landscapeVerifyChrome) {
            ReportComposerTopBar(
                compact = false,
                hasDraftContent = hasDraftContent,
                onOpenMenu = onOpenMenu,
                onClear = { showDiscardDialog = true }
            )
        }

        if (!state.draftLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
            }
            return@Column
        }

        when (state.stage) {
            SubmissionStage.PICK_MEDIA -> {
                val selectedComplaintOption = state.selectedComplaintId
                    ?.let { selectedId -> complaintOptions.firstOrNull { it.id == selectedId } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                if (selectedComplaintOption == null) "What happened?" else "Upload Photo of Complaint",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                if (selectedComplaintOption == null) {
                                    "Choose a complaint type, then pick a photo or video. We'll help verify the plate, time, and NYC address next."
                                } else {
                                    "${selectedComplaintOption.title} selected. Pick a photo or video to continue."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    state.validationErrors.media?.let { message ->
                        item {
                            ValidationMessage(
                                message = message,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (selectedComplaintOption != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                UploadTile(
                                    modifier = Modifier.weight(1f),
                                    showImage = BuildConfig.SHOW_COMPLAINT_IMAGES,
                                    onClick = {
                                        pendingMultipleSelection = false
                                        pendingMediaForCurrentReport = false
                                        pendingComplaintId = selectedComplaintOption.id
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                    }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(complaintOptions.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                row.forEach { option ->
                                    ComplaintTile(
                                        option = option,
                                        modifier = Modifier.weight(1f),
                                        animate = complaintOptions.indexOfFirst { it.id == option.id } == activeAnimatedOptionIndex,
                                        showImage = BuildConfig.SHOW_COMPLAINT_IMAGES,
                                        onClick = {
                                            pendingMultipleSelection = false
                                            pendingMediaForCurrentReport = false
                                            pendingComplaintId = option.id
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                        }
                                    )
                                }
                                if (row.size == 1) {
                                    UploadTile(
                                        modifier = Modifier.weight(1f),
                                        showImage = BuildConfig.SHOW_COMPLAINT_IMAGES,
                                        onClick = {
                                            pendingMultipleSelection = false
                                            pendingMediaForCurrentReport = false
                                            pendingComplaintId = null
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SubmissionStage.VERIFY -> {
                val selectedCandidate = remember(state.plateCandidates, state.selectedPlateCandidate) {
                    state.plateCandidates.firstOrNull { it.plate == state.selectedPlateCandidate }
                        ?: state.plateCandidates.firstOrNull()
                }
                val launchMediaPicker = {
                    pendingComplaintId = state.selectedComplaintId
                    pendingMultipleSelection = state.primaryMedia != null
                    pendingMediaForCurrentReport = state.primaryMedia == null
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }
                val submitReport = {
                    if (isAuthorized) {
                        vm.onAction(ComposerAction.SubmitPressed)
                    } else {
                        onRequireLogin {
                            vm.onAction(ComposerAction.SubmitPressed)
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > maxHeight
                    LaunchedEffect(isLandscape) {
                        isLandscapeLayout = isLandscape
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLandscape) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ReportComposerTopBar(
                                        compact = true,
                                        hasDraftContent = false,
                                        onOpenMenu = onOpenMenu,
                                        onClear = { showDiscardDialog = true }
                                    )
                                    VerifyMediaPanel(
                                        state = state,
                                        selectedCandidate = selectedCandidate,
                                        onLaunchMediaPicker = launchMediaPicker,
                                        landscapeSubmit = null,
                                        onPlateCandidateTapped = { pendingPlateCandidate = it },
                                        onRemoveMedia = { vm.onAction(ComposerAction.MediaRemoved(it)) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxSize()
                                ) {
                                    LazyColumn(
                                        state = verifyListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = if (isKeyboardVisible) 24.dp else 88.dp)
                                    ) {
                                        item {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                VerifyFieldsPanel(
                                                    state = state,
                                                    complaintOptions = complaintOptions,
                                                    isLandscape = true,
                                                    onAction = vm::onAction,
                                                    onShowComplaintChooser = { showComplaintChooser = true },
                                                    onShowPlateCandidates = { showPlateCandidates = true },
                                                    onShowAddressMap = { showAddressMap = true }
                                                )
                                            }
                                        }
                                    }
                                    if (!isKeyboardVisible) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ExtendedFloatingActionButton(
                                                onClick = { showDiscardDialog = true },
                                                icon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                                                text = { Text("Clear") },
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                contentColor = MaterialTheme.colorScheme.primary
                                            )
                                            ExtendedFloatingActionButton(
                                                onClick = {
                                                    if (!state.submitting) submitReport()
                                                },
                                                icon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                                text = { Text(if (state.submitting) "Submitting" else "Submit") },
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = verifyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = if (isKeyboardVisible) 24.dp else 112.dp)
                            ) {
                                item {
                                    ScreenSection(title = null) {
                                        VerifyMediaPanel(
                                            state = state,
                                            selectedCandidate = selectedCandidate,
                                            onLaunchMediaPicker = launchMediaPicker,
                                            landscapeSubmit = null,
                                            onPlateCandidateTapped = { pendingPlateCandidate = it },
                                            onRemoveMedia = { vm.onAction(ComposerAction.MediaRemoved(it)) }
                                        )
                                        VerifyFieldsPanel(
                                            state = state,
                                            complaintOptions = complaintOptions,
                                            isLandscape = false,
                                            onAction = vm::onAction,
                                            onShowComplaintChooser = { showComplaintChooser = true },
                                            onShowPlateCandidates = { showPlateCandidates = true },
                                            onShowAddressMap = { showAddressMap = true }
                                        )
                                    }
                                }
                            }

                            if (!isKeyboardVisible) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    color = Color.Transparent
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        SubmitReportButton(
                                            submitting = state.submitting,
                                            onClick = submitReport
                                        )
                                    }
                                }
                            }
                        }
                        if (state.detectingPlates && !state.awaitingVideoProcessingDecision && detectionProgressMinimized) {
                            DetectionProgressChip(
                                message = state.detectionMessage ?: "Detecting plates",
                                progress = state.detectionProgress,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                onClick = { detectionProgressMinimized = false }
                            )
                        }
                        state.detectionResultMessage?.let { message ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.detectingPlates && !state.awaitingVideoProcessingDecision && !detectionProgressMinimized) {
        DetectionProgressDialog(
            message = state.detectionMessage ?: "Detecting plates",
            progress = state.detectionProgress,
            videoUri = state.primaryMedia?.takeIf { it.isVideo }?.uri,
            frameTimeMs = if (videoScanPaused) videoPreviewScrubMs else state.detectionFrameTimeMs,
            videoDurationMs = state.detectionVideoDurationMs,
            paused = videoScanPaused,
            framePreviewUri = state.detectionFramePreviewUri,
            frameCandidates = state.detectionFrameCandidates,
            allCandidates = state.plateCandidates,
            selectedPlate = state.selectedPlateCandidate,
            onCandidateSelected = { candidate ->
                videoScanJob?.cancel()
                vm.onAction(ComposerAction.PlateCandidateChosen(candidate))
                vm.onAction(ComposerAction.DetectionFinished(
                    candidates = state.plateCandidates.ifEmpty { listOf(candidate) },
                    inferredPlate = candidate.plate,
                    inferredState = candidate.state
                ))
            },
            onPausedChange = { paused ->
                videoScanPaused = paused
                if (paused) {
                    videoPreviewScrubMs = state.detectionFrameTimeMs
                }
            },
            onSeekFrame = { timeMs ->
                videoPreviewScrubMs = timeMs
            },
            onMinimize = { detectionProgressMinimized = true }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetectionProgressDialog(
    message: String,
    progress: Float,
    videoUri: String?,
    frameTimeMs: Long,
    videoDurationMs: Long,
    paused: Boolean,
    framePreviewUri: String?,
    frameCandidates: List<PlateCandidate>,
    allCandidates: List<PlateCandidate>,
    selectedPlate: String?,
    onCandidateSelected: (PlateCandidate) -> Unit,
    onPausedChange: (Boolean) -> Unit,
    onSeekFrame: (Long) -> Unit,
    onMinimize: () -> Unit
) {
    val isVideoScan = videoUri != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onMinimize,
        sheetState = sheetState,
        dragHandle = null,
        modifier = if (isVideoScan) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .then(if (isVideoScan) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVideoScan) "Scanning video" else "Scanning photo",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onMinimize) {
                    Text("Minimize")
                }
            }
            Column(
                modifier = if (isVideoScan) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isVideoScan) {
                    VideoFrameScrubberPreview(
                        videoUri = checkNotNull(videoUri),
                        frameTimeMs = frameTimeMs,
                        candidates = frameCandidates,
                        selectedPlate = selectedPlate,
                        onCandidateSelected = onCandidateSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .height(360.dp)
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isVideoScan) {
                    VideoScanControls(
                        frameTimeMs = frameTimeMs,
                        videoDurationMs = videoDurationMs,
                        paused = paused,
                        frameCandidates = frameCandidates,
                        allCandidates = allCandidates,
                        selectedPlate = selectedPlate,
                        onCandidateSelected = onCandidateSelected,
                        onPausedChange = onPausedChange,
                        onSeekFrame = onSeekFrame
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoScanControls(
    frameTimeMs: Long,
    videoDurationMs: Long,
    paused: Boolean,
    frameCandidates: List<PlateCandidate>,
    allCandidates: List<PlateCandidate>,
    selectedPlate: String?,
    onCandidateSelected: (PlateCandidate) -> Unit,
    onPausedChange: (Boolean) -> Unit,
    onSeekFrame: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onPausedChange(!paused) }) {
            Text(if (paused) "Resume" else "Pause")
        }
        Text(
            text = formatDuration(frameTimeMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = frameTimeMs.toFloat().coerceIn(0f, videoDurationMs.coerceAtLeast(1L).toFloat()),
            onValueChange = { value -> onSeekFrame(value.toLong()) },
            enabled = paused && videoDurationMs > 0L,
            valueRange = 0f..videoDurationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(videoDurationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = "Current frame",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (frameCandidates.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frameCandidates) { candidate ->
                    DetectionCandidateButton(
                        candidate = candidate,
                        selected = selectedPlate == candidate.plate,
                        onClick = { onCandidateSelected(candidate) }
                    )
                }
            }
        }
    }
    if (allCandidates.isNotEmpty()) {
        Text(
            text = "Possible plates",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .verticalScroll(rememberScrollState())
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allCandidates.forEach { candidate ->
                    DetectionCandidateButton(
                        candidate = candidate,
                        selected = selectedPlate == candidate.plate,
                        onClick = { onCandidateSelected(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFrameScrubberPreview(
    videoUri: String,
    frameTimeMs: Long,
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    onCandidateSelected: (PlateCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            playWhenReady = false
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            prepare()
            pause()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(frameTimeMs, player) {
        player.pause()
        player.seekTo(frameTimeMs.coerceAtLeast(0L))
    }
    Box(
        modifier = modifier
            .background(Color.Black, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player }
        )
        PlateCandidateOverlay(
            candidates = candidates,
            selectedPlate = selectedPlate,
            onCandidateSelected = onCandidateSelected,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PlateCandidateOverlay(
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    onCandidateSelected: (PlateCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val overlayCandidates = candidates.filter { it.hasOverlayBounds() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            overlayCandidates.forEach { candidate ->
                val left = candidate.boundsLeft!! * size.width
                val top = candidate.boundsTop!! * size.height
                val right = candidate.boundsRight!! * size.width
                val bottom = candidate.boundsBottom!! * size.height
                val strokeColor = if (candidate.plate == selectedPlate) Color(0xFF00C853) else Color(0xFFFF6A2A)
                if (candidate.cornerPoints.size >= 8) {
                    val path = Path().apply {
                        moveTo(candidate.cornerPoints[0] * size.width, candidate.cornerPoints[1] * size.height)
                        lineTo(candidate.cornerPoints[2] * size.width, candidate.cornerPoints[3] * size.height)
                        lineTo(candidate.cornerPoints[4] * size.width, candidate.cornerPoints[5] * size.height)
                        lineTo(candidate.cornerPoints[6] * size.width, candidate.cornerPoints[7] * size.height)
                        close()
                    }
                    drawPath(path = path, color = strokeColor, style = Stroke(width = 4f))
                } else {
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
        overlayCandidates.forEach { candidate ->
            val left = maxWidth * candidate.boundsLeft!!
            val top = maxHeight * candidate.boundsTop!!
            Surface(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .clickable { onCandidateSelected(candidate) },
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.78f)
            ) {
                Text(
                    text = listOfNotNull(
                        candidate.plate,
                        candidate.state,
                        "${(candidate.confidence * 100).roundToInt()}%"
                    ).joinToString("  "),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

private fun PlateCandidate.hasOverlayBounds(): Boolean {
    val left = boundsLeft ?: return false
    val top = boundsTop ?: return false
    val right = boundsRight ?: return false
    val bottom = boundsBottom ?: return false
    return right > left && bottom > top
}

@Composable
private fun DetectionCandidateButton(
    candidate: PlateCandidate,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = candidate.plate,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = listOfNotNull(
                    candidate.state,
                    "${(candidate.confidence * 100).roundToInt()}%"
                ).joinToString("  "),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun DetectionProgressChip(
    message: String,
    progress: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class ComplaintOption(
    val id: String,
    val title: String,
    val imageUri: String,
    val lottieAssetPath: String? = null
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportComposerTopBar(
    compact: Boolean,
    hasDraftContent: Boolean,
    onOpenMenu: () -> Unit,
    onClear: () -> Unit
) {
    if (compact) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
            ) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
            }
            Text(
                text = "New Report",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (hasDraftContent) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(40.dp)
                ) {
                    Text("Clear")
                }
            }
        }
    } else {
        CenterAlignedTopAppBar(
            title = { Text("New Report") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                }
            },
            actions = {
                if (hasDraftContent) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }
        )
    }
}

@Composable
private fun VerifyMediaPanel(
    state: ComposerUiState,
    selectedCandidate: PlateCandidate?,
    onLaunchMediaPicker: () -> Unit,
    landscapeSubmit: (() -> Unit)? = null,
    onPlateCandidateTapped: (PlateCandidate) -> Unit,
    onRemoveMedia: (SubmissionMedia) -> Unit
) {
    val primaryMedia = state.primaryMedia
    if (primaryMedia != null) {
        PrimaryMediaPreview(
            primaryMedia = primaryMedia,
            extraMedia = state.extraMedia,
            primaryFocalPointX = selectedCandidate?.focalPointX,
            primaryFocalPointY = selectedCandidate?.focalPointY,
            plateCandidates = state.plateCandidates,
            selectedCandidate = selectedCandidate,
            selectedPlate = state.selectedPlateCandidate,
            onPlateCandidateTapped = onPlateCandidateTapped,
            onRemoveMedia = onRemoveMedia
        )
    } else {
        PrimaryMediaPlaceholder(
            isError = state.validationErrors.media != null,
            onClick = onLaunchMediaPicker
        )
    }
    if (landscapeSubmit != null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                if (state.primaryMedia == null) "Add photo or video" else "Add more photos or videos",
                onClick = onLaunchMediaPicker
            )
            SubmitReportButton(
                submitting = state.submitting,
                onClick = landscapeSubmit
            )
        }
    } else {
        SecondaryButton(
            if (state.primaryMedia == null) "Add photo or video" else "Add more photos or videos",
            onClick = onLaunchMediaPicker
        )
    }
    state.validationErrors.media?.let { ValidationMessage(it) }
}

@Composable
private fun VerifyFieldsPanel(
    state: ComposerUiState,
    complaintOptions: List<ComplaintOption>,
    isLandscape: Boolean,
    onAction: (ComposerAction) -> Unit,
    onShowComplaintChooser: () -> Unit,
    onShowPlateCandidates: () -> Unit,
    onShowAddressMap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReportedSelectionField(
            label = "Complaint",
            value = complaintOptions.firstOrNull { it.id == state.selectedComplaintId }?.title ?: "Select complaint",
            modifier = Modifier.weight(1.05f),
            onClick = onShowComplaintChooser,
            isError = state.validationErrors.complaint != null
        )
        ReportedField(
            label = "Plate",
            value = state.plate,
            onValueChange = { onAction(ComposerAction.FieldsChanged(plate = it.take(8))) },
            modifier = Modifier.weight(0.9f),
            isError = state.validationErrors.plate != null,
            trailingContent = if (state.plateCandidates.isNotEmpty()) {
                {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 24.dp)
                            .clickable { onShowPlateCandidates() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                null
            }
        )
        PlateRegionPickerField(
            label = "State",
            value = state.plateRegion,
            onSelected = { onAction(ComposerAction.FieldsChanged(plateRegion = it)) },
            modifier = Modifier.weight(0.32f),
            isError = state.validationErrors.plateRegion != null
        )
    }
    FieldErrorRow(
        listOf(
            state.validationErrors.complaint,
            state.validationErrors.plate,
            state.validationErrors.plateRegion
        )
    )
    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AddressField(
                    state = state,
                    onAction = onAction,
                    onShowAddressMap = onShowAddressMap
                )
                state.validationErrors.address?.let { ValidationMessage(it) }
            }
            Column(modifier = Modifier.weight(1f)) {
                OccurredAtField(
                    label = "Occurred At",
                    isoValue = state.occurredAtIso,
                    onValueSelected = { onAction(ComposerAction.FieldsChanged(occurredAtIso = it)) },
                    isError = state.validationErrors.occurredAt != null,
                    onClear = { onAction(ComposerAction.FieldsChanged(occurredAtIso = "")) }
                )
                state.validationErrors.occurredAt?.let { ValidationMessage(it) }
            }
        }
    } else {
        AddressField(
            state = state,
            onAction = onAction,
            onShowAddressMap = onShowAddressMap
        )
        state.validationErrors.address?.let { ValidationMessage(it) }
    }
    if (state.lookupInFlight) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Searching NYC addresses")
        }
    }
    state.addressSuggestions.forEach { suggestion ->
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(ComposerAction.AddressChosen(suggestion)) }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                Text(suggestion.label)
            }
        }
    }
    if (!isLandscape) {
        OccurredAtField(
            label = "Occurred At",
            isoValue = state.occurredAtIso,
            onValueSelected = { onAction(ComposerAction.FieldsChanged(occurredAtIso = it)) },
            isError = state.validationErrors.occurredAt != null,
            onClear = { onAction(ComposerAction.FieldsChanged(occurredAtIso = "")) }
        )
        state.validationErrors.occurredAt?.let { ValidationMessage(it) }
    }
    ReportedField(
        "Description (public facing)",
        state.description,
        onValueChange = { onAction(ComposerAction.FieldsChanged(description = it)) }
    )
    ReportedField(
        "Notes (for your records)",
        state.notes,
        onValueChange = { onAction(ComposerAction.FieldsChanged(notes = it)) }
    )
}

@Composable
private fun AddressField(
    state: ComposerUiState,
    onAction: (ComposerAction) -> Unit,
    onShowAddressMap: () -> Unit
) {
    ReportedField(
        "Address",
        state.addressQuery,
        onValueChange = { onAction(ComposerAction.AddressQueryChanged(it)) },
        isError = state.validationErrors.address != null,
        onClear = { onAction(ComposerAction.AddressQueryChanged("")) },
        trailingContent = {
            IconButton(onClick = onShowAddressMap) {
                Icon(Icons.Outlined.Map, contentDescription = "Pick address on map")
            }
        }
    )
}

@Composable
private fun SubmitReportButton(
    submitting: Boolean,
    onClick: () -> Unit
) {
    PrimaryButton(
        "Submit Report",
        onClick = onClick,
        enabled = !submitting
    )
}

@Composable
private fun FieldErrorRow(errors: List<String?>) {
    val messages = errors.filterNotNull()
    if (messages.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        messages.forEach { message ->
            ValidationMessage(message)
        }
    }
}

@Composable
private fun ValidationMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ComplaintTile(
    option: ComplaintOption,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    showImage: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showImage) {
                ComplaintTileArt(
                    option = option,
                    animate = animate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            } else {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ComplaintTileArt(
    option: ComplaintOption,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val artBackground = if (option.id == "ran_red_light") {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val artModifier = modifier
        .background(artBackground, shape)
        .clip(shape)
    val lottieScale = if (option.id == "drove_recklessly" || option.id == "ran_red_light") {
        ContentScale.Crop
    } else {
        ContentScale.Fit
    }
    if (option.lottieAssetPath != null) {
        val composition by rememberLottieComposition(LottieCompositionSpec.Asset(option.lottieAssetPath))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            isPlaying = animate,
            iterations = 1,
            restartOnPlay = true
        )
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { if (animate) progress else 0f },
                modifier = artModifier,
                contentScale = lottieScale
            )
            return
        }
    }

    AsyncImage(
        model = remember(option.imageUri) {
            ImageRequest.Builder(context)
                .data(option.imageUri)
                .apply {
                    if (option.imageUri.endsWith(".svg", ignoreCase = true)) {
                        decoderFactory(SvgDecoder.Factory())
                    }
                }
                .build()
        },
        contentDescription = option.title,
        modifier = artModifier,
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun UploadTile(
    modifier: Modifier = Modifier,
    showImage: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(18.dp)
                                .size(34.dp)
                        )
                    }
                }
            } else {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                "Add media",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PrimaryMediaPreview(
    primaryMedia: SubmissionMedia,
    extraMedia: List<SubmissionMedia>,
    primaryFocalPointX: Float? = null,
    primaryFocalPointY: Float? = null,
    plateCandidates: List<PlateCandidate> = emptyList(),
    selectedCandidate: PlateCandidate? = null,
    selectedPlate: String? = null,
    onPlateCandidateTapped: (PlateCandidate) -> Unit,
    onRemoveMedia: (SubmissionMedia) -> Unit
) {
    val mediaItems = remember(primaryMedia, extraMedia) { listOf(primaryMedia) + extraMedia }
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    var fullScreenMedia by remember { mutableStateOf<SubmissionMedia?>(null) }
    var fullScreenVideoCandidate by remember { mutableStateOf<PlateCandidate?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val media = mediaItems[page]
                    var imageSize by remember(media.uri) { mutableStateOf<Size?>(null) }
                    val imageAlignment = if (page == 0 && primaryFocalPointX != null && primaryFocalPointY != null) {
                        BiasAlignment(
                            horizontalBias = (primaryFocalPointX * 2f - 1f).coerceIn(-1f, 1f),
                            verticalBias = (primaryFocalPointY * 2f - 1f).coerceIn(-1f, 1f)
                        )
                    } else {
                        Alignment.Center
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        if (media.isVideo) {
                            val videoPreviewCandidate = selectedCandidate
                                ?.takeIf { page == 0 && it.videoFramePreviewUri != null }
                            if (videoPreviewCandidate?.videoFramePreviewUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(videoPreviewCandidate.videoFramePreviewUri)
                                        .crossfade(false)
                                        .build(),
                                    contentDescription = "Selected video frame",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { fullScreenVideoCandidate = videoPreviewCandidate },
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.72f)
                                ) {
                                    Text(
                                        text = "Video frame ${formatDuration(videoPreviewCandidate.videoFrameTimeMs ?: 0L)}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedCandidate?.let { fullScreenVideoCandidate = it }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
                                    Text(media.displayName)
                                }
                            }
                        } else {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = media.displayName,
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { fullScreenMedia = media },
                                contentScale = ContentScale.Crop,
                                alignment = imageAlignment
                            )
                            if (page == 0 && plateCandidates.isNotEmpty()) {
                                LicensePlateBoundsOverlay(
                                    candidates = plateCandidates,
                                    selectedPlate = selectedPlate,
                                    imageSize = imageSize,
                                    alignment = imageAlignment,
                                    contentScale = ContentScale.Crop,
                                    onCandidateTapped = onPlateCandidateTapped,
                                    onEmptyTap = { fullScreenMedia = media }
                                )
                            }
                        }
                    }
                }
                if (mediaItems.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(mediaItems.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .background(
                                        if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .size(width = if (pagerState.currentPage == index) 18.dp else 8.dp, height = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        fullScreenMedia?.let { media ->
            FullScreenMediaViewer(
                media = media,
                plateCandidates = if (media.uri == primaryMedia.uri) plateCandidates else emptyList(),
                selectedPlate = selectedPlate,
                onDismiss = { fullScreenMedia = null }
            )
        }
        fullScreenVideoCandidate?.let { candidate ->
            FullScreenVideoViewer(
                media = primaryMedia,
                candidate = candidate,
                onDismiss = { fullScreenVideoCandidate = null }
            )
        }
        val currentMedia = mediaItems.getOrNull(pagerState.currentPage)
        if (currentMedia != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-8).dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                IconButton(
                    onClick = { onRemoveMedia(currentMedia) }
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove image")
                }
            }
        }
    }
}

@Composable
private fun PrimaryMediaPlaceholder(
    isError: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isError) Color(0xFFFFF1F1) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(32.dp)
                    )
                }
                Text(
                    "Add photo or video",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LicensePlateBoundsOverlay(
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    imageSize: Size?,
    alignment: Alignment,
    contentScale: ContentScale = ContentScale.Crop,
    inputEnabled: Boolean = true,
    onCandidateTapped: (PlateCandidate) -> Unit,
    onEmptyTap: () -> Unit
) {
    val unselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val selectedColor = Color(0xFF20B15A)
    val density = LocalDensity.current
    val normalStroke = with(density) { 2.dp.toPx() }
    val selectedStroke = with(density) { 3.dp.toPx() }
    val touchSlopPx = with(density) { 10.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (inputEnabled) {
                    Modifier.pointerInput(candidates, imageSize, alignment, contentScale) {
                        detectTapGestures { offset ->
                            val viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                            val tappedCandidate = candidates
                                .asReversed()
                                .firstOrNull { candidate ->
                                    plateCandidatePolygon(
                                        candidate = candidate,
                                        viewportSize = viewportSize,
                                        imageSize = imageSize,
                                        alignment = alignment,
                                        contentScale = contentScale,
                                        inflateByPx = touchSlopPx
                                    )?.containsPoint(offset) == true
                                }
                            if (tappedCandidate != null) {
                                onCandidateTapped(tappedCandidate)
                            } else {
                                onEmptyTap()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        candidates.forEach { candidate ->
            val isSelected = candidate.plate == selectedPlate
            val points = plateCandidatePolygon(
                candidate = candidate,
                viewportSize = size,
                imageSize = imageSize,
                alignment = alignment,
                contentScale = contentScale,
                inflateByPx = 0f
            ) ?: return@forEach
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(
                path = path,
                color = if (isSelected) selectedColor else unselectedColor,
                style = Stroke(width = if (isSelected) selectedStroke else normalStroke)
            )
        }
    }
}

private fun plateCandidatePolygon(
    candidate: PlateCandidate,
    viewportSize: Size,
    imageSize: Size?,
    alignment: Alignment,
    contentScale: ContentScale,
    inflateByPx: Float
): List<Offset>? {
    val sourceWidth = imageSize?.width?.takeIf { it.isFinite() && it > 0f } ?: viewportSize.width
    val sourceHeight = imageSize?.height?.takeIf { it.isFinite() && it > 0f } ?: viewportSize.height
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

    if (candidate.hasUsableCornerPoints()) {
        val points = (0 until 8 step 2).map { index ->
            Offset(
                x = imageLeft + candidate.cornerPoints[index].coerceIn(0f, 1f) * renderedWidth,
                y = imageTop + candidate.cornerPoints[index + 1].coerceIn(0f, 1f) * renderedHeight
            )
        }
        return if (inflateByPx > 0f) points.inflateFromCentroid(inflateByPx) else points
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
    val rectPoints = listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
        Offset(rect.left, rect.bottom)
    )
    return rectPoints
}

private fun PlateCandidate.hasUsableCornerPoints(): Boolean {
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
    val widthA = (points[1] - points[0]).getDistance()
    val widthB = (points[2] - points[3]).getDistance()
    val heightA = (points[3] - points[0]).getDistance()
    val heightB = (points[2] - points[1]).getDistance()
    val averageWidth = (widthA + widthB) / 2f
    val averageHeight = (heightA + heightB) / 2f
    if (averageWidth <= 0f || averageHeight <= 0f) return false
    val aspect = averageWidth / averageHeight
    return aspect in 1.6f..8.5f
}

private fun List<Offset>.inflateFromCentroid(amount: Float): List<Offset> {
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

private fun List<Offset>.containsPoint(point: Offset): Boolean {
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

@Composable
private fun FullScreenVideoViewer(
    media: SubmissionMedia,
    candidate: PlateCandidate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(media.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(media.uri)))
            playWhenReady = true
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(candidate.videoFrameTimeMs, player) {
        player.seekTo(candidate.videoFrameTimeMs?.coerceAtLeast(0L) ?: 0L)
        player.play()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    update = { it.player = player }
                )
                PlateCandidateOverlay(
                    candidates = listOf(candidate),
                    selectedPlate = candidate.plate,
                    onCandidateSelected = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(44.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.62f)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close video",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenMediaViewer(
    media: SubmissionMedia,
    plateCandidates: List<PlateCandidate>,
    selectedPlate: String?,
    onDismiss: () -> Unit
) {
    var scale by remember(media.uri) { mutableStateOf(1f) }
    var offsetX by remember(media.uri) { mutableStateOf(0f) }
    var offsetY by remember(media.uri) { mutableStateOf(0f) }
    var imageSize by remember(media.uri) { mutableStateOf<Size?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
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
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (plateCandidates.isNotEmpty()) {
                    LicensePlateBoundsOverlay(
                        candidates = plateCandidates,
                        selectedPlate = selectedPlate,
                        imageSize = imageSize,
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Fit,
                        inputEnabled = false,
                        onCandidateTapped = {},
                        onEmptyTap = {}
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(44.dp),
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddressMapSheet(
    initialLatLng: LatLng,
    initialAddress: String,
    onDismiss: () -> Unit,
    onLocationSettled: (AddressSuggestion) -> Unit
) {
    val initialSuggestion = remember(initialLatLng, initialAddress) {
        initialAddress.takeIf { it.isNotBlank() }?.let {
            AddressSuggestion(
                label = it,
                latitude = initialLatLng.latitude,
                longitude = initialLatLng.longitude
            )
        }
    }
    var resolvedAddress by remember(initialAddress) { mutableStateOf(initialAddress) }
    var pendingSuggestion by remember(initialSuggestion) { mutableStateOf(initialSuggestion) }
    var lookupInFlight by remember { mutableStateOf(false) }
    val addressCache = remember { mutableMapOf<Pair<Long, Long>, AddressSuggestion>() }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 16f)
    }
    val mapUiSettings = remember {
        MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = true,
            scrollGesturesEnabledDuringRotateOrZoom = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = true
        )
    }

    LaunchedEffect(initialLatLng) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(initialLatLng, 16f)
    }

    suspend fun resolvePosition(latLng: LatLng) {
        val cacheKey = addressCacheKey(latLng)
        addressCache[cacheKey]?.let { cached ->
            resolvedAddress = cached.label
            pendingSuggestion = cached
            lookupInFlight = false
            return
        }

        lookupInFlight = true
        val suggestion = reverseGeocodeNyc(latLng.latitude, latLng.longitude)
            ?: AddressSuggestion(
                label = "${latLng.latitude}, ${latLng.longitude}",
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
        addressCache[cacheKey] = suggestion
        resolvedAddress = suggestion.label
        pendingSuggestion = suggestion
        lookupInFlight = false
    }

    LaunchedEffect(Unit) {
        if (initialAddress.isBlank()) {
            resolvePosition(initialLatLng)
        }
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collectLatest { isMoving ->
                if (!isMoving) {
                    delay(650)
                    resolvePosition(cameraPositionState.position.target)
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = maxWidth > maxHeight
                if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MessageCard("Set REPORTED_ANDROID_GOOGLE_MAPS_API_KEY in your environment to enable the map picker.")
                    }
                } else if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AddressMapCanvas(
                            cameraPositionState = cameraPositionState,
                            mapUiSettings = mapUiSettings,
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxSize()
                        )
                        AddressMapControls(
                            resolvedAddress = resolvedAddress,
                            lookupInFlight = lookupInFlight,
                            pendingSuggestion = pendingSuggestion,
                            onDismiss = onDismiss,
                            onDone = {
                                pendingSuggestion?.let(onLocationSettled)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxSize()
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AddressMapTopBar(
                            pendingSuggestion = pendingSuggestion,
                            onDismiss = onDismiss,
                            onDone = {
                                pendingSuggestion?.let(onLocationSettled)
                                onDismiss()
                            }
                        )
                        AddressMapStatus(
                            resolvedAddress = resolvedAddress,
                            lookupInFlight = lookupInFlight,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AddressMapCanvas(
                            cameraPositionState = cameraPositionState,
                            mapUiSettings = mapUiSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressMapTopBar(
    pendingSuggestion: AddressSuggestion?,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
        Text(
            text = "Pan map to change address.",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(
            onClick = onDone,
            enabled = pendingSuggestion != null
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun AddressMapControls(
    resolvedAddress: String,
    lookupInFlight: Boolean,
    pendingSuggestion: AddressSuggestion?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pan map to change address.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            AddressMapStatus(
                resolvedAddress = resolvedAddress,
                lookupInFlight = lookupInFlight,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.End + WindowInsetsSides.Bottom
                    )
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SecondaryButton("Cancel", onClick = onDismiss)
            }
            Box(modifier = Modifier.weight(1f)) {
                PrimaryButton(
                    "Done",
                    onClick = onDone,
                    enabled = pendingSuggestion != null
                )
            }
        }
    }
}

@Composable
private fun AddressMapStatus(
    resolvedAddress: String,
    lookupInFlight: Boolean,
    modifier: Modifier = Modifier
) {
    if (resolvedAddress.isBlank() && !lookupInFlight) return
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (lookupInFlight) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(
            text = if (lookupInFlight) "Finding address" else resolvedAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddressMapCanvas(
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    mapUiSettings: MapUiSettings,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = mapUiSettings
        )
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-18).dp)
                .size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = "Selected location",
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp)
                .size(width = 2.dp, height = 20.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun addressCacheKey(latLng: LatLng): Pair<Long, Long> {
    val cellMeters = 3.048
    val metersPerLatDegree = 111_320.0
    val metersPerLngDegree = metersPerLatDegree * abs(cos(Math.toRadians(latLng.latitude))).coerceAtLeast(0.000001)
    return (latLng.latitude * metersPerLatDegree / cellMeters).roundToLong() to
        (latLng.longitude * metersPerLngDegree / cellMeters).roundToLong()
}

@Composable
private fun PlateCandidatePickerDialog(
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    onDismiss: () -> Unit,
    onSelected: (PlateCandidate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Possible plates") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(candidates, key = { it.plate }) { candidate ->
                    val isSelected = candidate.plate == selectedPlate
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(candidate) },
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) Color(0xFF20B15A) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PlateCandidateThumbnail(candidate = candidate)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(candidate.plate, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${(candidate.confidence * 100).toInt()}% confidence",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                candidate.stateClassifierText()?.let { stateText ->
                                    Text(
                                        stateText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                candidate.plateTypeText()?.let { typeText ->
                                    Text(
                                        typeText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Text(
                                    "Selected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF20B15A)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun PlateCandidateThumbnail(candidate: PlateCandidate) {
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (candidate.thumbnailUri != null) {
            AsyncImage(
                model = candidate.thumbnailUri,
                contentDescription = candidate.plate,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("No image", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun List<PlateCandidate>.bestCenteredCandidate(): PlateCandidate? =
    maxByOrNull { candidate ->
        val centerX = candidate.focalPointX ?: 0.5f
        val centerY = candidate.focalPointY ?: 0.5f
        val distanceFromCenter = kotlin.math.sqrt(
            ((centerX - 0.5f) * (centerX - 0.5f) + (centerY - 0.5f) * (centerY - 0.5f)).toDouble()
        ).toFloat()
        val centerScore = (1f - (distanceFromCenter / 0.70710677f)).coerceIn(0f, 1f)
        candidate.confidence * 0.72f + centerScore * 0.28f
    }

private fun PlateCandidate.stateClassifierText(): String? {
    val detectedState = state ?: return null
    val confidenceText = stateConfidence?.let { " (${(it * 100).toInt()}%)" }.orEmpty()
    return "State classifier: $detectedState$confidenceText"
}

private fun PlateCandidate.plateTypeText(): String? {
    val label = plateTypeLabel ?: return null
    return "Plate type: $label"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    isAuthorized: Boolean,
    onRequireLogin: () -> Unit,
    onOpenMenu: () -> Unit,
    vm: ReportsViewModel = viewModel()
) {
    if (!isAuthorized) {
        LoginRequiredScreen(
            title = "My Reports",
            message = "Sign in to see your history and keep track of the reports you've submitted.",
            onLogin = onRequireLogin
        )
        return
    }

    val state by vm.state.collectAsState()
    var expandedReports by remember { mutableStateOf(setOf<String>()) }
    var pendingDeleteReport by remember { mutableStateOf<ReportSummary?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.reports.size, state.hasMore, state.loadingMore, state.loading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalItems = listState.layoutInfo.totalItemsCount
                if (totalItems > 0 && lastVisibleIndex >= totalItems - 4) {
                    vm.loadNextPage()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        pendingDeleteReport?.let { report ->
            AlertDialog(
                onDismissRequest = { pendingDeleteReport = null },
                title = { Text("Delete report?") },
                text = {
                    Text("This pending report for ${listOf(report.plateRegion, report.plate).filter { it.isNotBlank() }.joinToString(" ")} will be removed.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.deleteReport(report)
                            pendingDeleteReport = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteReport = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        CenterAlignedTopAppBar(
            title = { Text("My Reports") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenSection {
                    Text(
                        "Choose how you want to pull your reports. We will not load the list until you ask.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SecondaryButton(
                            "Search",
                            onClick = vm::chooseSearch,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            "List",
                            onClick = vm::chooseList,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (state.mode == ReportsMode.SEARCH) {
                item {
                    ScreenSection(title = "Search Filters") {
                        ReportedField(
                            label = "License plate",
                            value = state.licenseQuery,
                            onValueChange = { vm.updateSearch(license = it) }
                        )
                        OccurredAtField(
                            label = "Start Date",
                            isoValue = state.startDate,
                            onValueSelected = { vm.updateSearch(startDate = it) },
                            onClear = { vm.updateSearch(startDate = "") }
                        )
                        OccurredAtField(
                            label = "End Date",
                            isoValue = state.endDate,
                            onValueSelected = { vm.updateSearch(endDate = it) },
                            onClear = { vm.updateSearch(endDate = "") }
                        )
                        PrimaryButton(
                            "Search reports",
                            onClick = vm::search,
                            enabled = !state.loading
                        )
                    }
                }
            }

            if (state.error != null) {
                item {
                    ScreenSection(title = "Error") {
                        MessageCard(state.error ?: "Unknown error")
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.mode != null) {
                item {
                    ScreenSection(title = "Results") {
                        Text(
                            if (state.reports.isEmpty()) "No reports found." else "${state.reports.size} reports",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            items(state.reports, key = { it.objectId.ifBlank { it.id.toString() } }) { report ->
                val reportKey = report.objectId.ifBlank { report.id.toString() }
                val expanded = reportKey in expandedReports
                val detail = state.reportDetails[report.objectId] ?: report
                OutlinedCard(
                    modifier = Modifier.clickable {
                            expandedReports = if (expanded) {
                                expandedReports - reportKey
                            } else {
                                if (report.objectId.isNotBlank()) vm.loadDetail(report.objectId)
                                expandedReports + reportKey
                            }
                        },
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    report.street.ifBlank { "Unknown address" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    listOf(report.plate, report.plateRegion).filter { it.isNotBlank() }.joinToString(" - ")
                                        .ifBlank { "No plate captured" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                report.status.ifBlank { "Pending" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        val incidentAtDisplay = remember(report.incidentAt) { report.incidentAt.toReportDateTimeDisplay() }
                        if (incidentAtDisplay.isNotBlank()) {
                            Text(incidentAtDisplay, style = MaterialTheme.typography.bodySmall)
                        }
                        if (report.complaint.isNotBlank()) {
                            Text(report.complaint, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (expanded) {
                            if (report.objectId in state.detailLoadingIds) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            if (detail.description.isNotBlank()) {
                                Text(detail.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (detail.notes.isNotBlank()) {
                                Text(
                                    "Notes: ${detail.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val mediaUrls = detail.mediaUrls
                            val videoUrls = detail.videoUrls
                            if (mediaUrls.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(mediaUrls, key = { it }) { url ->
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Report photo",
                                            modifier = Modifier
                                                .size(112.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            if (videoUrls.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    videoUrls.forEachIndexed { index, _ ->
                                        Text(
                                            "Video ${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (mediaUrls.isEmpty() && videoUrls.isEmpty() && report.objectId !in state.detailLoadingIds) {
                                Text(
                                    "No media attached.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (report.canDelete) {
                                val isDeleting = reportKey in state.deletingReportKeys
                                TextButton(
                                    onClick = { pendingDeleteReport = report },
                                    enabled = !isDeleting
                                ) {
                                    Text(if (isDeleting) "Deleting..." else "Delete report")
                                }
                            }
                        }
                    }
                }
            }
            if (state.loadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isAuthorized: Boolean,
    onRequireLogin: () -> Unit,
    onLogout: () -> Unit,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onOpenMenu: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    if (!isAuthorized) {
        LoginRequiredScreen(
            title = "Profile",
            message = "Sign in to edit your profile, sync your settings, and manage your account.",
            onLogin = onRequireLogin
        )
        return
    }

    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var mediaScannerEnabled by remember { mutableStateOf(MediaScannerSettings.isEnabled(context)) }
    var offlineProcessingEnabled by remember { mutableStateOf(MediaScannerSettings.isOfflineProcessingEnabled(context)) }
    var notificationsEnabled by remember { mutableStateOf(MediaScannerSettings.hasNotificationPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted || MediaScannerSettings.hasNotificationPermission(context)
        MediaScannerSettings.markNotificationPermissionAsked(context)
    }
    val mediaScannerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = MediaScannerSettings.requiredPermissions().all { permission ->
            grants[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        mediaScannerEnabled = granted
        MediaScannerSettings.setEnabled(context, granted)
        if (granted) {
            MediaScannerScheduler.scanNow(context)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = { Text("Profile") },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenSection {
                    state.error?.let { MessageCard(it) }
                    ReportedField("First Name", state.firstName, { vm.update(firstName = it) }, enabled = state.editing)
                    ReportedField("Last Name", state.lastName, { vm.update(lastName = it) }, enabled = state.editing)
                    ReportedField("Phone", state.phone, { vm.update(phone = it) }, enabled = state.editing)
                    ReportedField("Email", state.email, { vm.update(email = it) }, enabled = state.editing)
                    ComplaintChipGroup(
                        selectedIds = listOf(state.themeMode.name),
                        options = listOf(
                            AppThemeMode.SYSTEM.name to "System",
                            AppThemeMode.LIGHT.name to "Light",
                            AppThemeMode.DARK.name to "Dark"
                        ),
                        onToggle = {
                            val mode = AppThemeMode.valueOf(it)
                            vm.setThemeMode(mode)
                            onThemeModeSelected(mode)
                        }
                    )
                    SettingsSwitchRow(
                        title = "Notifications",
                        description = "Allow Reported to notify you when a high-confidence infraction is detected.",
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val permission = MediaScannerSettings.notificationPermission()
                                if (permission != null && !MediaScannerSettings.hasNotificationPermission(context)) {
                                    notificationPermissionLauncher.launch(permission)
                                } else {
                                    notificationsEnabled = true
                                }
                            } else {
                                notificationsEnabled = false
                            }
                        }
                    )
                    SettingsSwitchRow(
                        title = "Allow offline photo processing to detect violations",
                        description = "Process new photos on this device only. Nothing uploads unless you choose to submit.",
                        checked = offlineProcessingEnabled,
                        onCheckedChange = { enabled ->
                            offlineProcessingEnabled = enabled
                            MediaScannerSettings.setOfflineProcessingEnabled(context, enabled)
                            if (enabled && mediaScannerEnabled) {
                                MediaScannerScheduler.scanNow(context)
                            }
                        }
                    )
                    SettingsSwitchRow(
                        title = "Media scanner",
                        description = "Watch for new photos and queue a local scan when offline processing is allowed.",
                        checked = mediaScannerEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (MediaScannerSettings.hasRequiredPermissions(context)) {
                                    mediaScannerEnabled = true
                                    MediaScannerSettings.setEnabled(context, true)
                                    if (offlineProcessingEnabled) {
                                        MediaScannerScheduler.scanNow(context)
                                    }
                                } else {
                                    mediaScannerPermissionLauncher.launch(MediaScannerSettings.requiredPermissions())
                                }
                            } else {
                                mediaScannerEnabled = false
                                MediaScannerSettings.setEnabled(context, false)
                            }
                        }
                    )
                    if (state.editing) {
                        PrimaryButton("Save", onClick = { vm.save {} }, enabled = !state.loading)
                    } else {
                        PrimaryButton("Edit Profile", onClick = vm::toggleEditing)
                    }
                    Text(
                        text = "Logout",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onLogout)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

private fun String.toReportDateTimeDisplay(): String {
    if (isBlank()) return ""
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    return try {
        OffsetDateTime.parse(this).atZoneSameInstant(zoneId).format(formatter)
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(this).atZone(zoneId).format(formatter)
        } catch (_: DateTimeParseException) {
            this
        }
    }
}
