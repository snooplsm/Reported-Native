package com.reported.nativeandroid.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import com.reported.nativeandroid.app.ComposerEvent
import com.reported.nativeandroid.app.ComposerUiState
import com.reported.nativeandroid.app.ComposerViewModel
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.PhiladelphiaSubmissionVideoMessage
import com.reported.nativeandroid.app.ProfileViewModel
import com.reported.nativeandroid.app.ReportsMode
import com.reported.nativeandroid.app.ReportsViewModel
import com.reported.nativeandroid.app.SharedMediaRequest
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.SubmissionStage
import com.reported.nativeandroid.app.isPhiladelphiaSubmission
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.ai.ReportedAiModelStore
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import com.reported.nativeandroid.report.NewReportTutorialSheet
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.reported.shared.model.AppThemeMode
import com.reported.shared.model.CityReportingRules
import com.reported.shared.model.ComplaintCategory
import com.reported.shared.model.PhiladelphiaMobilityAccessCatalogs
import com.reported.shared.model.PlatePatternClassifier
import com.reported.shared.model.ReportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val ROTATED_PLATE_OVERLAY_THRESHOLD_DEGREES = 4f
private const val REPORT_TUTORIAL_PREFS = "reported.report.tutorial"
private const val REPORT_TUTORIAL_SEEN_KEY = "new_report_tutorial_seen"
private val ReportedAiFabSize = 58.dp
private val ReportedAiFabVerifyBottomOffset = 76.dp
private val ReportedAiFabDefaultBottomOffset = 24.dp

private fun hasSeenReportTutorial(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(REPORT_TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .getBoolean(REPORT_TUTORIAL_SEEN_KEY, false)

private fun markReportTutorialSeen(context: Context) {
    context.applicationContext
        .getSharedPreferences(REPORT_TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(REPORT_TUTORIAL_SEEN_KEY, true)
        .apply()
}

private fun hasMediaLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportComposerScreen(
    isAuthorized: Boolean,
    onRequireLogin: ((() -> Unit)?) -> Unit,
    onOpenMenu: () -> Unit,
    sharedMediaRequest: SharedMediaRequest? = null,
    onSharedMediaConsumed: (Long) -> Unit = {},
    onReportSubmitted: (String) -> Unit = {},
    vm: ComposerViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val isScreenLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val verifyListState = rememberLazyListState()
    var isLandscapeLayout by remember { mutableStateOf(false) }
    val complaintOptions = remember(state.complaintCategories) {
        complaintOptionsFor(state.complaintCategories)
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
    var showVoiceAssistSheet by remember { mutableStateOf(false) }
    var voiceTranscript by remember { mutableStateOf("") }
    var voiceDraft by remember { mutableStateOf<VoiceReportDraft?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voiceProcessing by remember { mutableStateOf(false) }
    var voiceRecording by remember { mutableStateOf(false) }
    var voiceAmplitude by remember { mutableStateOf(0f) }
    var voiceModelInstalled by remember { mutableStateOf(OnDeviceGemmaVoiceDraftEngine.isModelInstalled(context)) }
    var voiceModelDownloading by remember { mutableStateOf(false) }
    var voiceModelDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var voiceAccelerationMessage by remember { mutableStateOf(ReportedAiModelStore.accelerationMessage(context)) }
    var voiceImageContext by remember { mutableStateOf<String?>(null) }
    var voiceImageContextJob by remember { mutableStateOf<Job?>(null) }
    var hasVoiceMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val voiceAudioRecorder = remember { VoiceReportAudioRecorder() }
    var showAddressSearchScreen by remember { mutableStateOf(false) }
    var showAddressMap by remember { mutableStateOf(false) }
    var showPlateCandidates by remember { mutableStateOf(false) }
    var pendingPlateCandidate by remember { mutableStateOf<PlateCandidate?>(null) }
    var detectionProgressMinimized by remember { mutableStateOf(false) }
    var metadataJob by remember { mutableStateOf<Job?>(null) }
    var videoScanJob by remember { mutableStateOf<Job?>(null) }
    var videoScanGeneration by remember { mutableIntStateOf(0) }
    var videoScanPaused by remember { mutableStateOf(false) }
    var videoPreviewScrubMs by remember { mutableLongStateOf(0L) }
    var addressSearchJob by remember { mutableStateOf<Job?>(null) }
    var mapLookupJob by remember { mutableStateOf<Job?>(null) }
    var showReportTutorial by remember { mutableStateOf(false) }
    var tutorialScannerEnabled by remember { mutableStateOf(true) }
    var tutorialNotificationsEnabled by remember { mutableStateOf(true) }
    var pendingTutorialMediaPermissionAfterNotification by remember { mutableStateOf(false) }
    var pendingTutorialCompleteAfterNotification by remember { mutableStateOf(false) }
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

    fun advanceAnimatedComplaint(fromIndex: Int) {
        if (animatedComplaintIndices.isEmpty() || activeAnimatedOptionIndex != fromIndex) return
        val currentPosition = animatedComplaintIndices.indexOf(fromIndex).takeIf { it >= 0 } ?: 0
        activeAnimatedOptionIndex = animatedComplaintIndices[(currentPosition + 1) % animatedComplaintIndices.size]
    }

    LaunchedEffect(animatedComplaintIndices) {
        activeAnimatedOptionIndex = when {
            animatedComplaintIndices.isEmpty() -> -1
            activeAnimatedOptionIndex in animatedComplaintIndices -> activeAnimatedOptionIndex
            else -> animatedComplaintIndices.first()
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collectLatest { event ->
            when (event) {
                is ComposerEvent.ReportSubmitted -> onReportSubmitted(event.objectId)
            }
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

    DisposableEffect(Unit) {
        onDispose {
            voiceImageContextJob?.cancel()
            voiceAudioRecorder.cancel()
        }
    }

    fun startVideoScan(
        videoMedia: SubmissionMedia,
        startTimeMs: Long = 0L,
        confirmProcessing: Boolean = false
    ) {
        if (confirmProcessing) {
            vm.onAction(ComposerAction.VideoProcessingDecision(true))
        }
        videoScanJob?.cancel()
        videoScanGeneration += 1
        videoScanPaused = false
        val scanGeneration = videoScanGeneration
        videoScanJob = scope.launch {
            val candidates = NativeAlprEngine.detectLicensePlatesInVideo(
                context = context,
                media = videoMedia,
                startTimeMs = startTimeMs,
                expectedComplaintHint = selectedComplaintDetectionHint(state, complaintOptions)
            ) { progress ->
                if (scanGeneration != videoScanGeneration) return@detectLicensePlatesInVideo
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
                    if (scanGeneration != videoScanGeneration) return@detectLicensePlatesInVideo
                    delay(80)
                }
            }
            if (scanGeneration != videoScanGeneration) return@launch
            val topCandidate = candidates.firstOrNull()
            vm.onAction(
                ComposerAction.DetectionFinished(
                    candidates = candidates,
                    inferredPlate = topCandidate?.plate,
                    inferredState = topCandidate?.state
                )
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
                Log.d(
                    "ReportedMetadata",
                    "Media metadata: isVideo=${media.isVideo}, occurredAt=${metadata.occurredAtIso}, lat=${metadata.latitude}, lon=${metadata.longitude}"
                )
                if (BuildConfig.DEBUG && !media.isVideo) {
                    val toastMessage = if (metadata.latitude != null && metadata.longitude != null) {
                        String.format(Locale.US, "Image GPS: %.6f, %.6f", metadata.latitude, metadata.longitude)
                    } else {
                        "No image GPS metadata found"
                    }
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).apply {
                        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 96)
                        show()
                    }
                }
                val metadataProvider = if (metadata.latitude != null && metadata.longitude != null) {
                    reportAddressProviderFor(context, metadata.latitude, metadata.longitude, "")
                } else {
                    null
                }
                if (media.isVideo && metadataProvider == ReportAddressProvider.Philadelphia) {
                    vm.onAction(ComposerAction.MediaRejected(media, PhiladelphiaSubmissionVideoMessage))
                    return@launch
                }
                val reverseGeocodeSuggestion = if (metadata.latitude != null && metadata.longitude != null) {
                    val provider = metadataProvider ?: reportAddressProviderFor(context, metadata.latitude, metadata.longitude, "")
                    vm.onAction(ComposerAction.DetectionProgressChanged(provider.reverseLookupLabel, 0.35f))
                    reverseGeocodeAddress(context, metadata.latitude, metadata.longitude)
                } else null
                vm.onAction(ComposerAction.MetadataApplied(
                    occurredAtIso = metadata.occurredAtIso,
                    photoOccurredAtIso = if (!media.isVideo) metadata.occurredAtIso else null,
                    latitude = metadata.latitude,
                    longitude = metadata.longitude,
                    inferredState = reverseGeocodeSuggestion?.region,
                    inferredAddress = reverseGeocodeSuggestion?.label,
                    photoAddressSuggestion = reverseGeocodeSuggestion
                ))
                if (!media.isVideo) {
                    vm.onAction(ComposerAction.DetectionProgressChanged("Detecting plates", 0.65f))
                    val candidates = NativeAlprEngine.detectLicensePlates(
                        context = context,
                        media = media,
                        expectedComplaintHint = selectedComplaintDetectionHint(vm.state.value, complaintOptions)
                    )
                    val topCandidate = candidates.firstOrNull()
                    vm.onAction(ComposerAction.DetectionFinished(
                        candidates = candidates,
                        inferredPlate = topCandidate?.plate,
                        inferredState = reverseGeocodeSuggestion?.region
                    ))
                    scope.launch {
                        VehicleImageDescriptionEngine.describeVehicle(context, media)?.let { vehicleDescription ->
                            if (vm.state.value.primaryMedia?.uri == media.uri) {
                                vm.onAction(ComposerAction.VehicleDescriptionApplied(vehicleDescription))
                            }
                        }
                    }
                }
            }
        }
    }

    fun inspectExtraMediaForPhiladelphiaVideo(media: SubmissionMedia) {
        if (!media.isVideo) return
        scope.launch {
            val metadata = extractSubmissionMetadata(context, media)
            val provider = if (metadata.latitude != null && metadata.longitude != null) {
                reportAddressProviderFor(context, metadata.latitude, metadata.longitude, "")
            } else {
                null
            }
            if (provider == ReportAddressProvider.Philadelphia) {
                vm.onAction(ComposerAction.MediaRejected(media, PhiladelphiaSubmissionVideoMessage))
            }
        }
    }

    fun addExtraMedia(uri: Uri) {
        val isVideo = isVideoUri(uri)
        scope.launch {
            val media = buildSubmissionMedia(context, uri, isVideo)
            vm.onAction(ComposerAction.ExtraMediaAdded(media))
            inspectExtraMediaForPhiladelphiaVideo(media)
        }
    }

    fun persistDocumentRead(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistDocumentRead(it)
            handleChosenMedia(it, pendingComplaintId, pendingMediaForCurrentReport)
        }
        pendingComplaintId = null
        pendingMediaForCurrentReport = false
        pendingMultipleSelection = false
    }

    val multiMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                persistDocumentRead(uri)
                val isVideo = isVideoUri(uri)
                val media = buildSubmissionMedia(context, uri, isVideo)
                vm.onAction(ComposerAction.ExtraMediaAdded(media))
                inspectExtraMediaForPhiladelphiaVideo(media)
            }
        }
        pendingMediaForCurrentReport = false
        pendingMultipleSelection = false
    }

    fun launchDocumentPicker() {
        val mimeTypes = if (vm.state.value.isPhiladelphiaSubmission()) {
            arrayOf("image/*")
        } else {
            arrayOf("image/*", "video/*")
        }
        if (pendingMultipleSelection) {
            multiMediaPicker.launch(mimeTypes)
        } else {
            mediaPicker.launch(mimeTypes)
        }
    }

    val tutorialMediaScannerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = MediaScannerSettings.requiredPermissions().all { permission ->
            grants[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        MediaScannerSettings.setOfflineProcessingEnabled(context, true)
        MediaScannerSettings.setEnabled(context, granted)
        if (granted) {
            MediaScannerScheduler.scanNow(context)
        }
    }

    fun finishReportTutorial() {
        markReportTutorialSeen(context)
        showReportTutorial = false
        MediaScannerSettings.setEnabled(context, false)
    }

    val tutorialNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        MediaScannerSettings.setNotificationsEnabled(context, granted || MediaScannerSettings.hasNotificationPermission(context))
        MediaScannerSettings.markNotificationPermissionAsked(context)
        if (pendingTutorialCompleteAfterNotification) {
            pendingTutorialCompleteAfterNotification = false
            finishReportTutorial()
            return@rememberLauncherForActivityResult
        }
        if (pendingTutorialMediaPermissionAfterNotification) {
            pendingTutorialMediaPermissionAfterNotification = false
            if (MediaScannerSettings.supportsBackgroundLibraryScanning()) {
                tutorialMediaScannerPermissionLauncher.launch(MediaScannerSettings.requiredPermissions())
            }
        }
    }

    val tutorialMetadataPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    fun processVoiceAudio(audioFile: File) {
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled(context)
        if (!voiceModelInstalled) {
            voiceError = "Install REPORTED AI before using Talk."
            runCatching { audioFile.delete() }
            return
        }
        voiceDraft = null
        voiceError = null
        voiceProcessing = true
        scope.launch {
            voiceImageContextJob?.join()
            voiceImageContextJob = null
            val voiceContext = VoiceReportContext.from(
                state = state,
                complaintOptions = complaintOptions,
                imageVisualContext = voiceImageContext,
                resolvedCurrentAddress = resolveVoiceCurrentAddress(context, state)
            )
            val draftResult = withTimeoutOrNull(120_000L) {
                OnDeviceGemmaVoiceDraftEngine.generateDraft(
                    context = context,
                    audioFile = audioFile,
                    complaintOptions = complaintOptions,
                    voiceContext = voiceContext
                )
            } ?: Result.failure(
                IllegalStateException("REPORTED AI is taking too long to process this recording. Try a shorter recording.")
            )
            draftResult.fold(
                onSuccess = { result ->
                    voiceTranscript = result.transcript.orEmpty()
                    voiceDraft = result.draft
                    voiceError = null
                },
                onFailure = { error ->
                    voiceError = error.message ?: "On-device Gemma could not process this recording."
                }
            )
            voiceProcessing = false
            runCatching {
                audioFile.delete()
            }
            voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled(context)
            voiceAccelerationMessage = ReportedAiModelStore.accelerationMessage(context)
        }
    }

    fun startVoiceImageContextWarmup() {
        voiceImageContextJob?.cancel()
        voiceImageContextJob = null
        voiceImageContext = null
        val media = state.primaryMedia?.takeIf { !it.isVideo } ?: return
        voiceImageContextJob = scope.launch {
            val imageInput = resolveVoiceReportImageInput(context, media)
            val imageContext = imageInput?.let {
                OnDeviceGemmaVoiceDraftEngine.generateImageContext(context, it.file)
            }
            if (imageInput?.deleteAfterUse == true) {
                runCatching { imageInput.file.delete() }
            }
            voiceImageContext = imageContext
        }
    }

    fun openVoiceAssistant() {
        ReportedAnalytics.logAiSparkleTapped(surface = "report_composer")
        voiceError = null
        voiceAccelerationMessage = ReportedAiModelStore.accelerationMessage(context)
        hasVoiceMicrophonePermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        showVoiceAssistSheet = true
        startVoiceImageContextWarmup()
    }

    fun openComplaintChooser(surface: String) {
        ReportedAnalytics.logComplaintChooserTapped(surface, state.selectedComplaintId)
        showComplaintChooser = true
    }

    fun openPlateChooser() {
        ReportedAnalytics.logPlateChooserTapped(
            candidateCount = state.plateCandidates.size,
            hasPlate = state.plate.isNotBlank()
        )
        showPlateCandidates = true
    }

    fun downloadVoiceModel() {
        if (voiceModelDownloading) return
        voiceError = null
        voiceModelDownloading = true
        voiceModelDownloadProgress = null
        scope.launch {
            OnDeviceGemmaVoiceDraftEngine.downloadModel(context) { downloadedBytes, totalBytes ->
                voiceModelDownloadProgress = if (totalBytes > 0L) {
                    (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    null
                }
            }.fold(
                onSuccess = {
                    voiceModelInstalled = true
                    voiceAccelerationMessage = ReportedAiModelStore.accelerationMessage(context)
                    voiceError = null
                    voiceModelDownloadProgress = 1f
                },
                onFailure = { error ->
                    voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled(context)
                    voiceAccelerationMessage = ReportedAiModelStore.accelerationMessage(context)
                    voiceError = error.message ?: "Could not install REPORTED AI."
                }
            )
            voiceModelDownloading = false
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasVoiceMicrophonePermission = granted
        if (granted) {
            voiceError = null
        } else {
            voiceError = "Microphone access is needed to talk through report fields."
        }
    }

    fun startVoiceCapture() {
        voiceModelInstalled = OnDeviceGemmaVoiceDraftEngine.isModelInstalled(context)
        if (!voiceModelInstalled) {
            voiceError = null
            return
        }
        hasVoiceMicrophonePermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasVoiceMicrophonePermission) {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voiceTranscript = ""
        voiceDraft = null
        voiceError = null
        voiceProcessing = false
        voiceAmplitude = 0f
        val started = voiceAudioRecorder.start(context) { amplitude ->
            scope.launch {
                voiceAmplitude = amplitude
            }
        }
        if (started) {
            voiceRecording = true
        } else {
            voiceImageContextJob?.cancel()
            voiceImageContextJob = null
            voiceError = voiceAudioRecorder.errorMessage ?: "Microphone recording could not start."
        }
    }

    fun stopVoiceCapture() {
        val audioFile = voiceAudioRecorder.stop()
        voiceRecording = false
        voiceAmplitude = 0f
        if (audioFile == null) {
            voiceError = voiceAudioRecorder.errorMessage ?: "I couldn't capture enough audio to process."
            return
        }
        processVoiceAudio(audioFile)
    }

    fun applyVoiceDraft(draft: VoiceReportDraft) {
        voiceImageContextJob?.cancel()
        voiceImageContextJob = null
        draft.complaintId?.let { vm.onAction(ComposerAction.SelectedComplaintChanged(it)) }
        if (
            draft.plate != null ||
            draft.plateRegion != null ||
            draft.address != null ||
            draft.description != null ||
            draft.notes != null ||
            draft.occurredAtIso != null
        ) {
            vm.onAction(
                ComposerAction.FieldsChanged(
                    plate = draft.plate,
                    plateRegion = draft.plateRegion,
                    address = draft.address,
                    description = draft.description,
                    notes = draft.notes,
                    occurredAtIso = draft.occurredAtIso
                )
            )
        }
        showVoiceAssistSheet = false
    }

    fun completeReportTutorialFromButton() {
        finishReportTutorial()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        launchDocumentPicker()
    }

    LaunchedEffect(Unit) { vm.onAction(ComposerAction.LoadDraft) }

    LaunchedEffect(state.draftLoaded) {
        if (state.draftLoaded && !hasSeenReportTutorial(context)) {
            tutorialScannerEnabled = false
            tutorialNotificationsEnabled = true
            showReportTutorial = true
        }
    }

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

    LaunchedEffect(showAddressSearchScreen, state.addressQuery) {
        addressSearchJob?.cancel()
        if (!showAddressSearchScreen || state.stage != SubmissionStage.VERIFY || state.addressQuery.isBlank() || state.addressQuery == state.address) {
            vm.onAction(ComposerAction.AddressSuggestionsChanged(emptyList()))
            return@LaunchedEffect
        }
        addressSearchJob = scope.launch {
            val provider = reportAddressProviderFor(
                context = context,
                latitude = state.latitude,
                longitude = state.longitude,
                address = state.addressQuery.ifBlank { state.address }
            )
            vm.onAction(ComposerAction.AddressLookupLoadingChanged(true))
            delay(250)
            vm.onAction(
                ComposerAction.AddressSuggestionsChanged(
                    searchReportAddresses(
                        context = context,
                        query = state.addressQuery,
                        latitude = state.latitude,
                        longitude = state.longitude,
                        address = state.addressQuery.ifBlank { state.address }
                    ).ifEmpty {
                        if (provider == ReportAddressProvider.Philadelphia) searchNycAddresses(state.addressQuery) else emptyList()
                    }
                )
            )
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
                                showImage = state.showComplaintImages,
                                onAnimationFinished = {
                                    advanceAnimatedComplaint(complaintOptions.indexOfFirst { it.id == option.id })
                                },
                                onClick = {
                                    ReportedAnalytics.logComplaintSelected(option.id, "pending_media")
                                    vm.onAction(ComposerAction.PendingComplaintConfirmed(option.id))
                                }
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

    if (showReportTutorial) {
        NewReportTutorialSheet(
            scannerAvailable = MediaScannerSettings.supportsBackgroundLibraryScanning(),
            scannerEnabled = tutorialScannerEnabled,
            onScannerEnabledChange = { tutorialScannerEnabled = it },
            notificationsEnabled = tutorialNotificationsEnabled,
            onNotificationsEnabledChange = { tutorialNotificationsEnabled = it },
            onRequestMediaLocationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasMediaLocationPermission(context)) {
                    tutorialMetadataPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }
            },
            onSkip = {
                markReportTutorialSeen(context)
                showReportTutorial = false
            },
            onComplete = ::completeReportTutorialFromButton
        )
    }

    if (showVoiceAssistSheet) {
        VoiceReportAssistantSheet(
            hasMicrophonePermission = hasVoiceMicrophonePermission,
            transcript = voiceTranscript,
            draft = voiceDraft,
            isRecording = voiceRecording,
            isProcessing = voiceProcessing,
            voiceAmplitude = voiceAmplitude,
            changeRows = voiceDraft?.changeRows(state, complaintOptions).orEmpty(),
            modelInstalled = voiceModelInstalled,
            modelDownloading = voiceModelDownloading,
            modelDownloadProgress = voiceModelDownloadProgress,
            modelDownloadSizeLabel = OnDeviceGemmaVoiceDraftEngine.ModelDownloadSizeLabel,
            accelerationMessage = voiceAccelerationMessage,
            error = voiceError,
            onRequestPermission = {
                voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDownloadModel = ::downloadVoiceModel,
            onTalk = ::startVoiceCapture,
            onStop = ::stopVoiceCapture,
            onClear = {
                voiceImageContextJob?.cancel()
                voiceImageContextJob = null
                voiceImageContext = null
                voiceAudioRecorder.cancel()
                voiceTranscript = ""
                voiceDraft = null
                voiceError = null
                voiceProcessing = false
                voiceRecording = false
                voiceAmplitude = 0f
            },
            onApply = ::applyVoiceDraft,
            onDismiss = {
                voiceImageContextJob?.cancel()
                voiceImageContextJob = null
                voiceAudioRecorder.cancel()
                voiceRecording = false
                voiceAmplitude = 0f
                showVoiceAssistSheet = false
            }
        )
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
                    startVideoScan(videoMedia = videoMedia, confirmProcessing = true)
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
                                    ReportedAnalytics.logComplaintSelected(option.id, "verify")
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

    state.plateCorrectionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { vm.onAction(ComposerAction.PlateCorrectionDismissed) },
            title = { Text("Fix plate format?") },
            text = {
                Text(
                    "This looks like a ${prompt.label} plate. Use ${prompt.suggestedPlate} instead of ${prompt.rawPlate}?"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.onAction(ComposerAction.PlateCorrectionKept) }) {
                        Text("Keep")
                    }
                    TextButton(onClick = { vm.onAction(ComposerAction.PlateCorrectionAccepted) }) {
                        Text("Use ${prompt.suggestedPlate}")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.onAction(ComposerAction.PlateCorrectionDismissed) }) {
                    Text("Edit")
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
            photoAddressSuggestion = state.photoAddressSuggestion,
            onDismiss = { showAddressMap = false },
            onLocationSettled = { suggestion ->
                vm.onAction(ComposerAction.AddressChosen(suggestion))
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
                    PlateCandidateThumbnail(
                        candidate = candidate,
                        sourceMediaUri = state.primaryMedia?.uri
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(candidate.plate, style = MaterialTheme.typography.titleLarge)
                        candidate.plateCorrectionText()?.let { correctionText ->
                            Text(
                                correctionText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

    if (showAddressSearchScreen) {
        AddressSearchScreen(
            state = state,
            onAction = vm::onAction,
            onBack = {
                showAddressSearchScreen = false
                vm.onAction(ComposerAction.AddressSuggestionsChanged(emptyList()))
            },
            onOpenMap = {
                showAddressSearchScreen = false
                showAddressMap = true
            }
        )
    } else if (showPlateCandidates) {
        PlateEntryScreen(
            state = state,
            onAction = vm::onAction,
            onBack = { showPlateCandidates = false },
            onCandidateSelected = { candidate ->
                vm.onAction(ComposerAction.PlateCandidateChosen(candidate))
                showPlateCandidates = false
            }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val landscapeColumnChrome = isScreenLandscape &&
                    (state.stage == SubmissionStage.VERIFY || state.stage == SubmissionStage.PICK_MEDIA)
                if (!landscapeColumnChrome) {
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
                if (isScreenLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(0.86f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ReportComposerTopBar(
                                compact = true,
                                hasDraftContent = false,
                                onOpenMenu = onOpenMenu,
                                onClear = { showDiscardDialog = true }
                            )
                            Text(
                                if (selectedComplaintOption == null) "What happened?" else "Upload Photo of Complaint",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            Text(
                                if (selectedComplaintOption == null) {
                                    "Choose a complaint type to pick a photo or video. We'll help verify the plate, time, and address next."
                                } else {
                                    "${selectedComplaintOption.title} selected. Pick a photo or video to continue."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1.14f)
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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
                                            showImage = state.showComplaintImages,
                                            onClick = {
                                                pendingMultipleSelection = false
                                                pendingMediaForCurrentReport = false
                                                pendingComplaintId = selectedComplaintOption.id
                                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                            }
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            } else {
                                items(complaintOptions.chunked(3)) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        row.forEach { option ->
                                            ComplaintTile(
                                                option = option,
                                                modifier = Modifier.weight(1f),
                                                animate = complaintOptions.indexOfFirst { it.id == option.id } == activeAnimatedOptionIndex,
                                                showImage = state.showComplaintImages,
                                                onAnimationFinished = {
                                                    advanceAnimatedComplaint(complaintOptions.indexOfFirst { it.id == option.id })
                                                },
                                                onClick = {
                                                    ReportedAnalytics.logComplaintSelected(option.id, "pick_media")
                                                    pendingMultipleSelection = false
                                                    pendingMediaForCurrentReport = false
                                                    pendingComplaintId = option.id
                                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                                }
                                            )
                                        }
                                        if (row.size < 3) {
                                            UploadTile(
                                                modifier = Modifier.weight(1f),
                                                showImage = state.showComplaintImages,
                                                onClick = {
                                                    pendingMultipleSelection = false
                                                    pendingMediaForCurrentReport = false
                                                    pendingComplaintId = null
                                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                                                }
                                            )
                                            repeat(2 - row.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
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
                                        "Choose a complaint type to pick a photo or video. We'll help verify the plate, time, and address next."
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
                                        showImage = state.showComplaintImages,
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
                                            showImage = state.showComplaintImages,
                                            onAnimationFinished = {
                                                advanceAnimatedComplaint(complaintOptions.indexOfFirst { it.id == option.id })
                                            },
                                            onClick = {
                                                ReportedAnalytics.logComplaintSelected(option.id, "pick_media")
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
                                            showImage = state.showComplaintImages,
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
                    val mediaToSubmitMillis = state.firstMediaAddedElapsedRealtimeMs
                        ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
                    ReportedAnalytics.logSubmitReportTapped(
                        stage = if (state.stage == SubmissionStage.VERIFY) "verify" else "pick_media",
                        isAuthorized = isAuthorized,
                        mediaToSubmitMillis = mediaToSubmitMillis
                    )
                    val readyToSubmit = vm.prepareSubmit()
                    if (!readyToSubmit) {
                        Unit
                    } else if (isAuthorized) {
                        vm.onAction(ComposerAction.SubmitPressed)
                    } else {
                        onRequireLogin {
                            vm.onAction(ComposerAction.SubmitPressed)
                        }
                    }
                }

                val reportedAiFabVisible = state.primaryMedia != null && !isKeyboardVisible
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > maxHeight
                    val reportedAiFabSpacer = if (reportedAiFabVisible) ReportedAiFabSize else 0.dp
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
                                    modifier = Modifier.weight(0.86f),
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        expandPreview = true,
                                        onLaunchMediaPicker = launchMediaPicker,
                                        landscapeSubmit = null,
                                        onPlateCandidateTapped = { pendingPlateCandidate = it },
                                        onPlateCandidateConfirmed = { vm.onAction(ComposerAction.PlateCandidateChosen(it)) },
                                        onShowPlateCandidates = ::openPlateChooser,
                                        onRemoveMedia = { vm.onAction(ComposerAction.MediaRemoved(it)) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1.14f)
                                        .fillMaxSize()
                                ) {
                                    LazyColumn(
                                        state = verifyListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .imePadding(),
                                        contentPadding = PaddingValues(
                                            bottom = if (isKeyboardVisible) 24.dp else 78.dp + reportedAiFabSpacer
                                        )
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
                                                    onShowComplaintChooser = { openComplaintChooser("verify") },
                                                    onShowPlateCandidates = ::openPlateChooser,
                                                    onShowAddressSearch = { showAddressSearchScreen = true },
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
                                                .padding(horizontal = 4.dp),
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
                                                text = { Text(if (state.submitting) "Submitting" else "Submit") },
                                                icon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null) },
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                                contentPadding = PaddingValues(
                                    bottom = if (isKeyboardVisible) 24.dp else 112.dp + reportedAiFabSpacer
                                )
                            ) {
                                item {
                                    ScreenSection(title = null) {
                                        VerifyMediaPanel(
                                            state = state,
                                            selectedCandidate = selectedCandidate,
                                            onLaunchMediaPicker = launchMediaPicker,
                                            landscapeSubmit = null,
                                            onPlateCandidateTapped = { pendingPlateCandidate = it },
                                            onPlateCandidateConfirmed = { vm.onAction(ComposerAction.PlateCandidateChosen(it)) },
                                            onShowPlateCandidates = ::openPlateChooser,
                                            onRemoveMedia = { vm.onAction(ComposerAction.MediaRemoved(it)) }
                                        )
                                        VerifyFieldsPanel(
                                            state = state,
                                            complaintOptions = complaintOptions,
                                            isLandscape = false,
                                            onAction = vm::onAction,
                                            onShowComplaintChooser = { openComplaintChooser("verify") },
                                            onShowPlateCandidates = ::openPlateChooser,
                                            onShowAddressSearch = { showAddressSearchScreen = true },
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
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        SubmitReportButton(
                                            submitting = state.submitting,
                                            progress = state.submitProgress,
                                            message = state.submitMessage,
                                            onClick = submitReport,
                                            edgeToEdge = true
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
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = message,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = { vm.onAction(ComposerAction.DetectionResultDismissed) }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss scan message")
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            }
            if (state.primaryMedia != null && !isKeyboardVisible) {
                ReportedAiFloatingActionButton(
                    onClick = ::openVoiceAssistant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            end = 20.dp,
                            bottom = if (state.stage == SubmissionStage.VERIFY) {
                                ReportedAiFabVerifyBottomOffset
                            } else {
                                ReportedAiFabDefaultBottomOffset
                            }
                        )
                )
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
                videoScanGeneration += 1
                videoScanJob?.cancel()
                vm.onAction(ComposerAction.PlateCandidateChosen(candidate))
                vm.onAction(ComposerAction.DetectionFinished(
                    candidates = state.plateCandidates.ifEmpty { listOf(candidate) },
                    inferredPlate = candidate.plate,
                    inferredState = candidate.state
                ))
            },
            onPausedChange = { paused ->
                if (paused) {
                    videoScanPaused = true
                    videoPreviewScrubMs = state.detectionFrameTimeMs
                } else {
                    val resumeTimeMs = videoPreviewScrubMs.coerceAtLeast(0L)
                    val shouldResumeFromScrubPosition =
                        state.primaryMedia?.isVideo == true &&
                            kotlin.math.abs(resumeTimeMs - state.detectionFrameTimeMs) > 250L
                    videoScanPaused = false
                    if (shouldResumeFromScrubPosition) {
                        state.primaryMedia?.let { videoMedia ->
                            startVideoScan(videoMedia = videoMedia, startTimeMs = resumeTimeMs)
                        }
                    }
                }
            },
            onSeekFrame = { timeMs ->
                videoPreviewScrubMs = timeMs
            },
            onCancel = {
                videoScanGeneration += 1
                videoScanJob?.cancel()
                videoScanPaused = false
                vm.onAction(ComposerAction.VideoProcessingCancelled)
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
    onCancel: () -> Unit,
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
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
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
                            .height(310.dp)
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
            candidate.plateCorrectionText()?.let { correctionText ->
                Text(
                    text = correctionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            }
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

data class ComplaintOption(
    val id: String,
    val title: String,
    val imageUri: String,
    val lottieAssetPath: String? = null
)

private fun ComplaintOption.isRanRedLightOrStopSign(): Boolean {
    val normalizedTitle = title.lowercase(Locale.US)
    return lottieAssetPath?.contains("ranredlight", ignoreCase = true) == true ||
        normalizedTitle.contains("red light") ||
        normalizedTitle.contains("stop sign")
}

private fun complaintTileTitleSize(title: String) = when {
    title.length >= 26 -> 11.sp
    title.length >= 20 -> 12.sp
    else -> 16.sp
}

fun complaintOptionsFor(categories: List<ComplaintCategory>): List<ComplaintOption> =
    listOf(
        complaintOptionFor(
            categories = categories,
            fallbackId = "blocked_bike_lane",
            fallbackTitle = "Blocked bike lane",
            imageUri = "file:///android_asset/complaints/bikelane.svg",
            keywords = listOf("bike lane")
        ),
        complaintOptionFor(
            categories = categories,
            fallbackId = "blocked_crosswalk",
            fallbackTitle = "Blocked crosswalk",
            imageUri = "file:///android_asset/complaints/crosswalk.svg",
            keywords = listOf("crosswalk")
        ),
        complaintOptionFor(
            categories = categories,
            fallbackId = "ran_red_light",
            fallbackTitle = "Ran red light",
            imageUri = "file:///android_asset/complaints/ranredlight.jpg",
            lottieAssetPath = "complaints/ranredlight.json",
            keywords = listOf("red light", "stop sign")
        ),
        complaintOptionFor(
            categories = categories,
            fallbackId = "drove_recklessly",
            fallbackTitle = "Drove recklessly",
            imageUri = "file:///android_asset/complaints/reckless.png",
            lottieAssetPath = "complaints/reckless.json",
            keywords = listOf("reckless", "aggressive")
        ),
        complaintOptionFor(
            categories = categories,
            fallbackId = "parked_illegally",
            fallbackTitle = "Parked illegally",
            imageUri = "file:///android_asset/complaints/parkedillegally.jpg",
            lottieAssetPath = "complaints/parkedillegally.json",
            keywords = listOf("parked")
        )
    )

private fun complaintOptionFor(
    categories: List<ComplaintCategory>,
    fallbackId: String,
    fallbackTitle: String,
    imageUri: String,
    keywords: List<String>,
    lottieAssetPath: String? = null
): ComplaintOption {
    val category = categories.firstOrNull { category ->
        val searchableText = "${category.name} ${category.key}".lowercase(Locale.US)
        keywords.any { searchableText.contains(it) }
    }
    return ComplaintOption(
        id = category?.id ?: fallbackId,
        title = category?.name ?: fallbackTitle,
        imageUri = imageUri,
        lottieAssetPath = lottieAssetPath
    )
}

private fun selectedComplaintDetectionHint(
    state: ComposerUiState,
    complaintOptions: List<ComplaintOption>
): String? {
    val selectedId = state.selectedComplaintId ?: return null
    val option = complaintOptions.firstOrNull { it.id == selectedId }
    return listOfNotNull(option?.title, option?.id, selectedId).joinToString(" ")
}

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
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Report",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
            title = {
                Box(contentAlignment = Alignment.Center) {
                    Text("Report")
                }
            },
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
private fun ReportedAiFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(ReportedAiFabSize),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedSparkleIcon(
                contentDescription = "Reported AI",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun AnimatedSparkleIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "sparkleIcon")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleScale"
    )
    val rotation by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1240, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleRotation"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    Icon(
        Icons.Outlined.AutoAwesome,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            rotationZ = rotation,
            alpha = alpha
        )
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VoiceReportAssistantSheet(
    hasMicrophonePermission: Boolean,
    transcript: String,
    draft: VoiceReportDraft?,
    isRecording: Boolean,
    isProcessing: Boolean,
    voiceAmplitude: Float,
    changeRows: List<VoiceDraftChangeRow>,
    modelInstalled: Boolean,
    modelDownloading: Boolean,
    modelDownloadProgress: Float?,
    modelDownloadSizeLabel: String,
    accelerationMessage: String,
    error: String?,
    onRequestPermission: () -> Unit,
    onDownloadModel: () -> Unit,
    onTalk: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onApply: (VoiceReportDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val maxRecordingMillis = 29_900L
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val rowFields = remember(changeRows) { changeRows.map { it.field } }
    var currentSourceFields by remember { mutableStateOf<Set<VoiceDraftField>>(emptySet()) }
    var undoCurrentSourceFields by remember { mutableStateOf<Set<VoiceDraftField>?>(null) }
    var pendingBulkSource by remember { mutableStateOf<VoiceDraftSource?>(null) }
    var elapsedMillis by remember(isRecording) { mutableLongStateOf(0L) }
    val contentScrollState = rememberScrollState()
    val reviewContentKey = remember(transcript, changeRows, draft) {
        listOf(
            transcript.trim(),
            changeRows.joinToString(",") { it.field.name },
            listOfNotNull(draft?.yearRange, draft?.make, draft?.model)
                .joinToString(",") { it.trim() }
        )
            .filter { it.isNotBlank() }
            .joinToString("|")
    }
    val activeHeaderSource = when {
        rowFields.isEmpty() -> null
        currentSourceFields.containsAll(rowFields) -> VoiceDraftSource.Current
        currentSourceFields.none { it in rowFields } -> VoiceDraftSource.ReportedAi
        else -> null
    }
    val reportedFields = rowFields.toSet() - currentSourceFields

    LaunchedEffect(isRecording) {
        elapsedMillis = 0L
        val startedAt = System.currentTimeMillis()
        while (isRecording) {
            elapsedMillis = (System.currentTimeMillis() - startedAt).coerceIn(0L, maxRecordingMillis)
            if (elapsedMillis >= maxRecordingMillis) {
                onStop()
                break
            }
            delay(100)
        }
    }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            sheetState.expand()
        }
    }

    LaunchedEffect(reviewContentKey) {
        if (reviewContentKey.isBlank() || isRecording || isProcessing) return@LaunchedEffect
        sheetState.expand()
        delay(180)
        val midpoint = (contentScrollState.maxValue * 0.45f).toInt().coerceAtLeast(0)
        contentScrollState.animateScrollTo(midpoint)
    }

    LaunchedEffect(rowFields) {
        currentSourceFields = emptySet()
        undoCurrentSourceFields = null
        pendingBulkSource = null
    }

    pendingBulkSource?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingBulkSource = null },
            title = { Text("Use ${source.title} for all fields?") },
            text = { Text("This changes every field in this Reported AI draft.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        undoCurrentSourceFields = currentSourceFields
                        currentSourceFields = if (source == VoiceDraftSource.Current) {
                            rowFields.toSet()
                        } else {
                            emptySet()
                        }
                        pendingBulkSource = null
                    }
                ) {
                    Text("Use ${source.title}")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkSource = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val floatingSheetShape = RoundedCornerShape(22.dp)
    ModalBottomSheet(
        modifier = Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        scrimColor = Color.Transparent,
        shape = floatingSheetShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val isMinimizedRecording = isRecording && sheetState.currentValue == SheetValue.PartiallyExpanded
        if (isMinimizedRecording) {
            VoiceMinimizedRecordingControl(
                amplitude = voiceAmplitude,
                elapsedMillis = elapsedMillis,
                maxMillis = maxRecordingMillis,
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 0.dp)
                    .verticalScroll(contentScrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedSparkleIcon(contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reported AI", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Dictate the complaint, plate, state, time, address, description, and notes, and we'll fill in the fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Dismiss voice assistant")
                }
            }

            if (accelerationMessage.isNotBlank()) {
                Text(
                    accelerationMessage,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!modelInstalled) {
                if (modelDownloading) {
                    if (modelDownloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { modelDownloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "Installing REPORTED AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PrimaryButton(
                        text = "Install REPORTED AI (${modelDownloadSizeLabel.toInstallSizeLabel()})",
                        onClick = onDownloadModel
                    )
                }
            } else if (!hasMicrophonePermission) {
                MessageCard("Microphone access is needed before you can talk through report fields.")
                PrimaryButton("Allow microphone", onClick = onRequestPermission)
            } else if (isProcessing) {
                VoiceProcessingPanel()
            } else {
                VoiceCaptureControl(
                    isRecording = isRecording,
                    amplitude = voiceAmplitude,
                    elapsedMillis = elapsedMillis,
                    maxMillis = maxRecordingMillis,
                    onClick = if (isRecording) onStop else onTalk
                )
            }

            error?.let { message ->
                ValidationMessage(message = message, modifier = Modifier.fillMaxWidth())
            }

            if (transcript.isNotBlank()) {
                VoiceAssistBlock(title = "Transcript", body = transcript)
            }

            if (draft != null) {
                if (changeRows.isNotEmpty()) {
                    VoiceAssistSectionHeader("Changes to apply")
                    VoiceDraftSourceHeader(
                        activeSource = activeHeaderSource,
                        canUndo = undoCurrentSourceFields != null,
                        onSelect = { pendingBulkSource = it },
                        onUndo = {
                            undoCurrentSourceFields?.let { undoFields ->
                                currentSourceFields = undoFields
                                undoCurrentSourceFields = null
                            }
                        }
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        changeRows.forEach { row ->
                            VoiceDraftChangePreviewRow(
                                row = row,
                                selectedSource = if (row.field in currentSourceFields) {
                                    VoiceDraftSource.Current
                                } else {
                                    VoiceDraftSource.ReportedAi
                                },
                                onSelect = { source ->
                                    undoCurrentSourceFields = null
                                    currentSourceFields = if (source == VoiceDraftSource.Current) {
                                        currentSourceFields + row.field
                                    } else {
                                        currentSourceFields - row.field
                                    }
                                }
                            )
                        }
                    }
                }
                if (draft.make != null || draft.model != null || draft.yearRange != null) {
                    VoiceAssistSectionHeader("Extracted vehicle")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        draft.yearRange?.let { VoiceDraftMetadataRow(label = "Vehicle Year", value = it) }
                        draft.make?.let { VoiceDraftMetadataRow(label = "Make", value = it) }
                        draft.model?.let { VoiceDraftMetadataRow(label = "Model", value = it) }
                    }
                }
                if (changeRows.isEmpty() && draft.make == null && draft.model == null && draft.yearRange == null) {
                    MessageCard("Reported AI did not find any form fields to update.")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton("Clear", onClick = onClear, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        "Fill form",
                        onClick = { onApply(draft.keepingReportedFields(reportedFields)) },
                        modifier = Modifier.weight(1f),
                        enabled = draft.keepingReportedFields(reportedFields).hasAnyFillableField
                    )
                }
        }
        }
    }
}

}

@Composable
private fun VoiceMinimizedRecordingControl(
    amplitude: Float,
    elapsedMillis: Long,
    maxMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (maxMillis > 0L) {
        (elapsedMillis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingMillis = (maxMillis - elapsedMillis).coerceAtLeast(0L)

    Row(
        modifier = modifier.heightIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VoiceLevelMeter(
            amplitude = amplitude,
            isActive = true,
            modifier = Modifier
                .width(94.dp)
                .height(30.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Recording",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${formatVoiceElapsedTenths(remainingMillis)} left",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${formatVoiceElapsedTenths(elapsedMillis)} / ${formatVoiceElapsedTenths(maxMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "STOP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
private fun VoiceCaptureControl(
    isRecording: Boolean,
    amplitude: Float,
    elapsedMillis: Long,
    maxMillis: Long,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.height(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceLevelMeter(
                amplitude = amplitude,
                isActive = isRecording,
                modifier = Modifier
                    .width(170.dp)
                    .height(26.dp)
                    .graphicsLayer(alpha = if (isRecording) 1f else 0f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    formatVoiceElapsedTenths(elapsedMillis),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isRecording) 1f else 0f)
                )
                Text(
                    "/ ${formatVoiceElapsedTenths(maxMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isRecording) 1f else 0f)
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(20.dp)
                .size(112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isRecording) "STOP" else "TALK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
                Icon(
                    if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.surface
                )
            }
        }
        }
    }

@Composable
private fun VoiceLevelMeter(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val level by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "voiceLevel"
    )
    val activeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(18) { index ->
            val pattern = ((index * 7) % 11) / 10f
            val liveHeight = 5.dp + ((8f + pattern * 15f) * level.coerceAtLeast(0.08f)).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isActive) liveHeight else 5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) activeColor else idleColor)
            )
        }
    }
}

@Composable
private fun VoiceProcessingPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                "Processing audio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        VoiceProcessingStep(text = "Recording captured", isComplete = true)
        VoiceProcessingStep(text = "Transcribing", isActive = true)
        VoiceProcessingStep(text = "Generating form fields", isDimmed = true)
    }
}

@Composable
private fun VoiceProcessingStep(
    text: String,
    isComplete: Boolean = false,
    isActive: Boolean = false,
    isDimmed: Boolean = false
) {
    val contentColor = when {
        isDimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> MaterialTheme.colorScheme.onSurface
                        isActive -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}

@Composable
private fun VoiceDraftChangePreviewRow(
    row: VoiceDraftChangeRow,
    selectedSource: VoiceDraftSource,
    onSelect: (VoiceDraftSource) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                row.label.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceDraftValueColumn(
                    value = row.currentValue,
                    isSelected = selectedSource == VoiceDraftSource.Current,
                    onClick = { onSelect(VoiceDraftSource.Current) },
                    modifier = Modifier.weight(1f)
                )
                VoiceDraftValueColumn(
                    value = row.nextValue,
                    isSelected = selectedSource == VoiceDraftSource.ReportedAi,
                    onClick = { onSelect(VoiceDraftSource.ReportedAi) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VoiceDraftValueColumn(
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedGreen = Color(0xFF2E7D32)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) selectedGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) selectedGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun VoiceDraftSourceHeader(
    activeSource: VoiceDraftSource?,
    canUndo: Boolean,
    onSelect: (VoiceDraftSource) -> Unit,
    onUndo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceDraftSourceHeaderButton(
                title = VoiceDraftSource.Current.title,
                isSelected = activeSource == VoiceDraftSource.Current,
                onClick = { onSelect(VoiceDraftSource.Current) },
                modifier = Modifier.weight(1f)
            )
            VoiceDraftSourceHeaderButton(
                title = VoiceDraftSource.ReportedAi.title,
                isSelected = activeSource == VoiceDraftSource.ReportedAi,
                onClick = { onSelect(VoiceDraftSource.ReportedAi) },
                modifier = Modifier.weight(1f)
            )
            if (canUndo) {
                TextButton(onClick = onUndo) {
                    Text("Undo")
                }
            }
        }
        if (activeSource == null) {
            Text(
                "Mixed field sources",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceDraftSourceHeaderButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedGreen = Color(0xFF2E7D32)
    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) selectedGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) selectedGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) selectedGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceAssistSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun VoiceDraftMetadataRow(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                label.uppercase(Locale.US),
                modifier = Modifier.width(102.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatVoiceElapsedTenths(milliseconds: Long): String {
    val seconds = (milliseconds.coerceIn(0L, 29_900L).toDouble() / 1000.0)
    return String.format(Locale.US, "%.1fs", seconds)
}

private fun String.toInstallSizeLabel(): String = replace(" ", "").lowercase(Locale.US)

@Composable
private fun VoiceAssistBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Text(
                body,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class VoiceReportGemmaResult(
    val transcript: String?,
    val draft: VoiceReportDraft
)

private data class VoiceReportContext(
    val currentDeviceTimeIso: String,
    val currentAddress: String?,
    val currentLatitude: Double?,
    val currentLongitude: Double?,
    val photoAddress: String?,
    val photoOccurredAtIso: String?,
    val imageVisualContext: String?,
    val currentOccurredAtIso: String?,
    val currentComplaintTitle: String?,
    val currentPlate: String?,
    val currentPlateRegion: String?,
    val currentDescription: String?,
    val currentNotes: String?
) {
    companion object {
        fun from(
            state: ComposerUiState,
            complaintOptions: List<ComplaintOption>,
            imageVisualContext: String? = null,
            resolvedCurrentAddress: String? = null
        ): VoiceReportContext {
            val currentAddress = cleaned(state.addressQuery.ifBlank { state.address })
                ?: cleaned(resolvedCurrentAddress)
            val usableCoordinates = isUsableCoordinate(state.latitude, state.longitude)
            val selectedComplaint = complaintOptions.firstOrNull { it.id == state.selectedComplaintId }
            return VoiceReportContext(
                currentDeviceTimeIso = OffsetDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                currentAddress = currentAddress,
                currentLatitude = state.latitude.takeIf { usableCoordinates },
                currentLongitude = state.longitude.takeIf { usableCoordinates },
                photoAddress = cleaned(state.photoAddressSuggestion?.label),
                photoOccurredAtIso = cleaned(state.photoOccurredAtIso),
                imageVisualContext = cleaned(imageVisualContext),
                currentOccurredAtIso = cleaned(state.occurredAtIso),
                currentComplaintTitle = cleaned(selectedComplaint?.title),
                currentPlate = cleaned(state.plate),
                currentPlateRegion = cleaned(state.plateRegion),
                currentDescription = cleaned(state.description),
                currentNotes = cleaned(state.notes)
            )
        }

        private fun cleaned(value: String?): String? = value
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        private fun isUsableCoordinate(latitude: Double?, longitude: Double?): Boolean {
            if (latitude == null || longitude == null) return false
            if (!latitude.isFinite() || !longitude.isFinite()) return false
            if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return false
            return abs(latitude) > 0.000001 || abs(longitude) > 0.000001
        }
    }

    fun toPromptBlock(): String {
        val lines = buildList {
            add("- currentDeviceTimeIso: $currentDeviceTimeIso")
            currentAddress?.let { add("- currentAddress: $it") }
            if (currentLatitude != null && currentLongitude != null) {
                add("- currentCoordinates: ${String.format(Locale.US, "%.6f, %.6f", currentLatitude, currentLongitude)}")
            }
            photoAddress?.let { add("- imageAddress: $it") }
            photoOccurredAtIso?.let { add("- imageOccurredAtIso: $it") }
            imageVisualContext?.let { add("- imageVisualContext: $it") }
            currentOccurredAtIso?.let { add("- currentOccurredAtIso: $it") }
            currentComplaintTitle?.let { add("- currentComplaint: $it") }
            currentPlate?.let { add("- currentPlate: $it") }
            currentPlateRegion?.let { add("- currentPlateState: $it") }
            currentDescription?.let { add("- currentDescription: $it") }
            currentNotes?.let { add("- currentNotes: $it") }
        }
        return lines.joinToString(separator = "\n")
    }
}

private suspend fun resolveVoiceCurrentAddress(
    context: Context,
    state: ComposerUiState
): String? {
    val existingAddress = state.addressQuery.ifBlank { state.address }.trim()
    if (existingAddress.isNotBlank()) return null
    val latitude = state.latitude
    val longitude = state.longitude
    if (latitude == null || longitude == null) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return null
    if (abs(latitude) <= 0.000001 && abs(longitude) <= 0.000001) return null
    return reverseGeocodeAddress(context, latitude, longitude)?.label
}

private data class VoiceReportImageInput(
    val file: File,
    val deleteAfterUse: Boolean
)

private fun resolveVoiceReportImageInput(
    context: Context,
    media: SubmissionMedia?
): VoiceReportImageInput? {
    if (media == null || media.isVideo) return null
    val uri = Uri.parse(media.uri)
    if (uri.scheme.equals("file", ignoreCase = true)) {
        val file = uri.path?.let(::File)
        if (file?.isFile == true && file.length() > 0L) {
            return VoiceReportImageInput(file = file, deleteAfterUse = false)
        }
    }

    val tempDirectory = File(context.cacheDir, "litertlm-images").apply { mkdirs() }
    val extension = media.displayName
        .substringAfterLast('.', "jpg")
        .replace(Regex("[^A-Za-z0-9]"), "")
        .ifBlank { "jpg" }
    val tempFile = File.createTempFile("reported_voice_image_", ".$extension", tempDirectory)
    return try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            tempFile.delete()
            return null
        }
        input.use { source ->
            tempFile.outputStream().use { target ->
                source.copyTo(target)
            }
        }
        if (tempFile.length() > 0L) {
            VoiceReportImageInput(file = tempFile, deleteAfterUse = true)
        } else {
            tempFile.delete()
            null
        }
    } catch (error: Throwable) {
        tempFile.delete()
        null
    }
}

private object OnDeviceGemmaVoiceDraftEngine {
    val ModelDownloadSizeLabel: String
        get() = ReportedAiModelStore.DownloadSizeLabel

    fun isModelInstalled(context: Context): Boolean = ReportedAiModelStore.isModelInstalled(context)

    suspend fun downloadModel(
        context: Context,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = ReportedAiModelStore.downloadModel(context, onProgress)

    suspend fun generateDraft(
        context: Context,
        audioFile: File,
        imageFile: File? = null,
        complaintOptions: List<ComplaintOption>,
        voiceContext: VoiceReportContext
    ): Result<VoiceReportGemmaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val modelFile = ReportedAiModelStore.resolveModelFile(context)
                ?: error(
                    "REPORTED AI is not installed. Install REPORTED AI (${ReportedAiModelStore.compactDownloadSizeLabel}) before voice drafting can run."
                )
            val prompt = buildVoiceReportGemmaPrompt(complaintOptions, voiceContext)
            if (audioFile.length() <= VoiceReportAudioRecorder.WAV_HEADER_BYTES) {
                error("The recording was too short to process.")
            }

            try {
                generateDraftResponse(
                    context = context,
                    modelFile = modelFile,
                    audioFile = audioFile,
                    imageFile = imageFile,
                    prompt = prompt,
                    complaintOptions = complaintOptions
                )
            } catch (error: Throwable) {
                if (imageFile == null) throw error
                generateDraftResponse(
                    context = context,
                    modelFile = modelFile,
                    audioFile = audioFile,
                    imageFile = null,
                    prompt = prompt,
                    complaintOptions = complaintOptions
                )
            }
        }
    }

    suspend fun generateImageContext(
        context: Context,
        imageFile: File
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val modelFile = ReportedAiModelStore.resolveModelFile(context) ?: return@runCatching null
            if (!imageFile.isFile || imageFile.length() <= 0L) return@runCatching null
            createEngineWithFallback(
                context = context,
                modelFile = modelFile,
                visionEnabled = true,
                audioEnabled = false
            ).use { engine ->
                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 0.1,
                        temperature = 0.0
                    )
                )
                engine.createConversation(conversationConfig).use { conversation ->
                    val response = conversation.sendMessage(
                        Contents.of(
                            Content.ImageFile(imageFile.absolutePath),
                            Content.Text(
                                "Briefly inspect this report photo for form-filling context. Return one concise sentence with only clearly visible facts: possible complaint type, vehicle make/model/color/type, visible license plate text, location clues, and scene details. If uncertain, say uncertain rather than guessing."
                            )
                        )
                    )
                    response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = " ") { it.text }
                        .ifBlank { response.toString() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(1200)
                        .takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private suspend fun generateDraftResponse(
        context: Context,
        modelFile: File,
        audioFile: File,
        imageFile: File?,
        prompt: String,
        complaintOptions: List<ComplaintOption>
    ): VoiceReportGemmaResult {
        createEngineWithFallback(
            context = context,
            modelFile = modelFile,
            visionEnabled = imageFile != null,
            audioEnabled = true
        ).use { engine ->
            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 1,
                    topP = 0.1,
                    temperature = 0.0
                )
            )
            engine.createConversation(conversationConfig).use { conversation ->
                val contents = buildList {
                    imageFile?.takeIf { it.isFile && it.length() > 0L }?.let {
                        add(Content.ImageFile(it.absolutePath))
                    }
                    add(Content.AudioFile(audioFile.absolutePath))
                    add(Content.Text(prompt))
                }
                val response = conversation.sendMessage(
                    Contents.of(contents)
                )
                val responseText = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "\n") { it.text }
                    .ifBlank { response.toString() }
                return resolveVoiceReportAddress(
                    context = context,
                    result = parseVoiceReportGemmaJson(responseText, complaintOptions)
                )
            }
        }
    }

    private fun createEngineWithFallback(
        context: Context,
        modelFile: File,
        visionEnabled: Boolean,
        audioEnabled: Boolean
    ): Engine {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir.orEmpty()
        val npuBackend = Backend.NPU(nativeLibraryDir = nativeLibraryDir)
        val attempts = listOf(
            VoiceBackendAttempt(
                label = "NPU",
                backend = npuBackend,
                visionBackend = npuBackend.takeIf { visionEnabled },
                audioBackend = npuBackend.takeIf { audioEnabled }
            ),
            VoiceBackendAttempt(
                label = "CPU",
                backend = Backend.CPU(),
                visionBackend = Backend.CPU().takeIf { visionEnabled },
                audioBackend = Backend.CPU().takeIf { audioEnabled }
            )
        )

        var lastFailure: Throwable? = null
        attempts.forEach { attempt ->
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = attempt.backend,
                visionBackend = attempt.visionBackend,
                audioBackend = attempt.audioBackend,
                cacheDir = ReportedAiModelStore.resolveCacheDir(context).absolutePath
            )
            val engine = Engine(engineConfig)
            try {
                engine.initialize()
                ReportedAiModelStore.setAccelerationStatus(
                    context,
                    if (attempt.label == "NPU") {
                        ReportedAiModelStore.AccelerationStatus.Accelerated
                    } else {
                        ReportedAiModelStore.AccelerationStatus.CpuFallback
                    }
                )
                Log.i(LogTag, "LiteRT-LM initialized with ${attempt.label} backend for ${modelFile.name}.")
                return engine
            } catch (error: Throwable) {
                runCatching { engine.close() }
                lastFailure = error
                Log.w(LogTag, "LiteRT-LM ${attempt.label} backend failed for ${modelFile.name}; trying fallback.", error)
            }
        }
        throw lastFailure ?: IllegalStateException("Could not initialize REPORTED AI.")
    }

    private data class VoiceBackendAttempt(
        val label: String,
        val backend: Backend,
        val visionBackend: Backend?,
        val audioBackend: Backend?
    )

    private const val LogTag = "ReportedAI"
}

private suspend fun resolveVoiceReportAddress(
    context: Context,
    result: VoiceReportGemmaResult
): VoiceReportGemmaResult {
    val rawAddress = result.draft.address
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return result
    val resolvedAddress = resolveVoiceAddressLabel(context, rawAddress) ?: return result
    if (resolvedAddress.equals(rawAddress, ignoreCase = true)) return result
    return result.copy(draft = result.draft.copy(address = resolvedAddress))
}

private suspend fun resolveVoiceAddressLabel(
    context: Context,
    rawAddress: String
): String? {
    parseVoiceCoordinate(rawAddress)?.let { (latitude, longitude) ->
        return reverseGeocodeAddress(context, latitude, longitude)?.label
    }
    val queries = voiceAddressSearchQueries(rawAddress)
    for (query in queries) {
        val suggestions = searchNycAddresses(query)
        val chosen = chooseVoiceAddressSuggestion(rawAddress, suggestions)
        if (chosen != null) return chosen.label
    }
    return null
}

private fun parseVoiceCoordinate(rawAddress: String): Pair<Double, Double>? {
    val match = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
        .find(rawAddress)
        ?: return null
    val latitude = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
    val longitude = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return null
    return latitude to longitude
}

private fun voiceAddressSearchQueries(rawAddress: String): List<String> {
    val cleaned = cleanVoiceAddressQuery(rawAddress)
    val variants = mutableListOf<String>()
    fun add(value: String?) {
        val normalized = value
            ?.trim(' ', ',', '.', ';')
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.length >= 4 }
            ?: return
        if (variants.none { it.equals(normalized, ignoreCase = true) }) {
            variants += normalized
        }
    }
    add(cleaned)
    val houseStreetMatch = Regex("""\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\s+(.+)$""")
        .find(cleaned)
    if (houseStreetMatch != null) {
        val houseNumber = houseStreetMatch.groupValues[1]
        val street = houseStreetMatch.groupValues[2]
            .split(Regex("""\b(?:new\s+york|ny|usa|united\s+states|apt|apartment|unit|floor|fl)\b""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim(' ', ',', '.', ';')
        add("$houseNumber $street")
    }
    return variants
}

private fun cleanVoiceAddressQuery(rawAddress: String): String =
    rawAddress
        .replace(Regex("""\b(?:address\s+is|address|near|at)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:new\s+york|nyc|ny|usa|united\s+states)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""[^\p{Alnum}\s-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun chooseVoiceAddressSuggestion(
    rawAddress: String,
    suggestions: List<AddressSuggestion>
): AddressSuggestion? {
    if (suggestions.isEmpty()) return null
    val expectedHouse = Regex("""\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\b""")
        .find(rawAddress)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase(Locale.US)
    val rawTokens = cleanVoiceAddressQuery(rawAddress)
        .lowercase(Locale.US)
        .split(Regex("""\s+"""))
        .filter { it.length >= 3 && !it.all(Char::isDigit) }
    fun score(suggestion: AddressSuggestion): Int {
        val label = suggestion.label.lowercase(Locale.US)
        var score = 0
        if (expectedHouse != null && expectedHouse in label) score += 6
        score += rawTokens.count { token -> token in label }
        if (suggestion.region == "NY") score += 1
        return score
    }
    return suggestions
        .maxByOrNull(::score)
        ?.takeIf { score(it) >= if (expectedHouse == null) 1 else 6 }
}

private class VoiceReportAudioRecorder {
    companion object {
        const val WAV_HEADER_BYTES = 44
        private const val SampleRateHz = 16_000
        private const val BitsPerSample = 16
        private const val ChannelCount = 1
    }

    @Volatile private var isRecording = false
    private var recorder: AudioRecord? = null
    private var outputFile: File? = null
    private var writerThread: Thread? = null
    @Volatile private var amplitudeCallback: ((Float) -> Unit)? = null
    var errorMessage: String? = null
        private set

    @SuppressLint("MissingPermission")
    fun start(context: Context, onAmplitude: (Float) -> Unit = {}): Boolean {
        cancel()
        errorMessage = null

        val minBufferSize = AudioRecord.getMinBufferSize(
            SampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            errorMessage = "This device cannot provide microphone audio in the format Gemma needs."
            return false
        }

        val meteringBufferSize = SampleRateHz / 10 * ChannelCount * BitsPerSample / 8
        val bufferSize = max(minBufferSize, meteringBufferSize)
        val file = runCatching {
            File.createTempFile("reported_voice_report_", ".wav", context.cacheDir)
        }.getOrElse {
            errorMessage = it.localizedMessage ?: "Could not create a temporary recording file."
            return false
        }

        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrElse {
            errorMessage = it.localizedMessage ?: "Could not open the microphone."
            runCatching { file.delete() }
            return false
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            errorMessage = "Could not initialize the microphone."
            audioRecord.release()
            runCatching { file.delete() }
            return false
        }

        runCatching {
            RandomAccessFile(file, "rw").use { output ->
                writeWavHeader(output, pcmDataLength = 0L)
            }
            audioRecord.startRecording()
        }.onFailure {
            errorMessage = it.localizedMessage ?: "Could not start microphone recording."
            audioRecord.release()
            runCatching { file.delete() }
            return false
        }

        recorder = audioRecord
        outputFile = file
        amplitudeCallback = onAmplitude
        isRecording = true
        writerThread = thread(start = true, name = "ReportedVoiceReportRecorder") {
            writeMicInput(audioRecord, file, bufferSize)
        }
        return true
    }

    fun stop(): File? {
        val file = outputFile
        isRecording = false
        writerThread?.join(1_500)
        writerThread = null
        recorder = null
        outputFile = null
        amplitudeCallback?.invoke(0f)
        amplitudeCallback = null
        return file?.takeIf { it.exists() && it.length() > WAV_HEADER_BYTES }
    }

    fun cancel() {
        val file = outputFile
        isRecording = false
        writerThread?.join(750)
        writerThread = null
        recorder = null
        outputFile = null
        amplitudeCallback?.invoke(0f)
        amplitudeCallback = null
        runCatching { file?.delete() }
    }

    private fun writeMicInput(audioRecord: AudioRecord, file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var pcmDataLength = 0L
        var lastAmplitudeEmitNanos = 0L
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.seek(WAV_HEADER_BYTES.toLong())
                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        pcmDataLength += read
                        val now = System.nanoTime()
                        if (now - lastAmplitudeEmitNanos >= 64_000_000L) {
                            lastAmplitudeEmitNanos = now
                            amplitudeCallback?.invoke(calculateAmplitude(buffer, read))
                        }
                    }
                }
                output.seek(0L)
                writeWavHeader(output, pcmDataLength)
            }
        } catch (error: Throwable) {
            errorMessage = error.localizedMessage ?: "Could not write microphone recording."
        } finally {
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
    }

    private fun calculateAmplitude(buffer: ByteArray, read: Int): Float {
        var sumSquares = 0.0
        var sampleCount = 0
        var index = 0
        while (index + 1 < read) {
            val low = buffer[index].toInt() and 0xff
            val high = buffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            sampleCount += 1
            index += 2
        }
        if (sampleCount == 0) return 0f
        val rms = sqrt(sumSquares / sampleCount.toDouble()) / Short.MAX_VALUE.toDouble()
        val decibels = 20.0 * log10(rms.coerceAtLeast(0.000_001))
        val normalized = ((decibels + 60.0) / 60.0).coerceIn(0.0, 1.0)
        return normalized.pow(1.35).toFloat().coerceIn(0f, 1f)
    }

    private fun writeWavHeader(output: RandomAccessFile, pcmDataLength: Long) {
        val byteRate = SampleRateHz * ChannelCount * BitsPerSample / 8
        val blockAlign = ChannelCount * BitsPerSample / 8
        output.writeBytes("RIFF")
        output.writeIntLe((36L + pcmDataLength).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(ChannelCount)
        output.writeIntLe(SampleRateHz)
        output.writeIntLe(byteRate)
        output.writeShortLe(blockAlign)
        output.writeShortLe(BitsPerSample)
        output.writeBytes("data")
        output.writeIntLe(pcmDataLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }
}

private data class VoiceDraftChangeRow(
    val field: VoiceDraftField,
    val label: String,
    val currentValue: String,
    val nextValue: String
)

private enum class VoiceDraftField {
    Complaint,
    Plate,
    State,
    Address,
    OccurredAt,
    Description,
    Notes
}

private enum class VoiceDraftSource(val title: String) {
    Current("Current"),
    ReportedAi("Reported AI")
}

private data class VoiceReportDraft(
    val complaintId: String? = null,
    val complaintTitle: String? = null,
    val plate: String? = null,
    val plateRegion: String? = null,
    val address: String? = null,
    val occurredAtIso: String? = null,
    val make: String? = null,
    val model: String? = null,
    val yearRange: String? = null,
    val description: String? = null,
    val notes: String? = null
) {
    val hasAnyFillableField: Boolean
        get() = listOf(complaintId, plate, plateRegion, address, occurredAtIso, description, notes).any { !it.isNullOrBlank() }

    fun keepingReportedFields(reportedFields: Set<VoiceDraftField>): VoiceReportDraft =
        copy(
            complaintId = complaintId.takeIf { VoiceDraftField.Complaint in reportedFields },
            complaintTitle = complaintTitle.takeIf { VoiceDraftField.Complaint in reportedFields },
            plate = plate.takeIf { VoiceDraftField.Plate in reportedFields },
            plateRegion = plateRegion.takeIf { VoiceDraftField.State in reportedFields },
            address = address.takeIf { VoiceDraftField.Address in reportedFields },
            occurredAtIso = occurredAtIso.takeIf { VoiceDraftField.OccurredAt in reportedFields },
            description = description.takeIf { VoiceDraftField.Description in reportedFields },
            notes = notes.takeIf { VoiceDraftField.Notes in reportedFields }
        )

    fun changeRows(
        currentState: ComposerUiState,
        complaintOptions: List<ComplaintOption>
    ): List<VoiceDraftChangeRow> = buildList {
        val currentComplaint = complaintOptions.firstOrNull { it.id == currentState.selectedComplaintId }?.title
        addVoiceChangeRow(VoiceDraftField.Complaint, "Complaint", currentComplaint, complaintTitle)
        addVoiceChangeRow(VoiceDraftField.Plate, "Plate", currentState.plate, plate)
        addVoiceChangeRow(VoiceDraftField.State, "State", currentState.plateRegion, plateRegion)
        addVoiceChangeRow(VoiceDraftField.Address, "Address", currentState.addressQuery.ifBlank { currentState.address }, address)
        addVoiceChangeRow(VoiceDraftField.OccurredAt, "Occurred At", currentState.occurredAtIso, occurredAtIso)
        addVoiceChangeRow(VoiceDraftField.Description, "Description", currentState.description, description)
        addVoiceChangeRow(VoiceDraftField.Notes, "Notes", currentState.notes, notes)
    }
}

private fun MutableList<VoiceDraftChangeRow>.addVoiceChangeRow(
    field: VoiceDraftField,
    label: String,
    currentValue: String?,
    nextValue: String?
) {
    val cleanedNext = nextValue.cleanedVoicePreviewValue() ?: return
    add(
        VoiceDraftChangeRow(
            field = field,
            label = label,
            currentValue = currentValue.cleanedVoicePreviewValue() ?: "Empty",
            nextValue = cleanedNext
        )
    )
}

private fun String?.cleanedVoicePreviewValue(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun generateVoiceReportDraft(
    transcript: String,
    complaintOptions: List<ComplaintOption>
): VoiceReportDraft {
    val cleanedTranscript = transcript.trim()
    val complaint = inferVoiceComplaint(cleanedTranscript, complaintOptions)
    return VoiceReportDraft(
        complaintId = complaint?.id,
        complaintTitle = complaint?.title,
        plate = inferVoicePlate(cleanedTranscript),
        plateRegion = inferVoicePlateRegion(cleanedTranscript),
        address = inferVoiceAddress(cleanedTranscript),
        occurredAtIso = inferVoiceOccurredAt(cleanedTranscript),
        make = inferVoiceVehicleMake(cleanedTranscript),
        model = inferVoiceVehicleModel(cleanedTranscript),
        yearRange = inferVoiceVehicleYearRange(cleanedTranscript),
        description = inferVoiceDescription(cleanedTranscript),
        notes = inferVoiceNotes(cleanedTranscript)
    )
}

private fun buildVoiceReportGemmaPrompt(
    complaintOptions: List<ComplaintOption>,
    voiceContext: VoiceReportContext
): String {
    val complaints = complaintOptions.joinToString(separator = "\n") { option ->
        "- ${option.id}: ${option.title}"
    }
    val contextBlock = voiceContext.toPromptBlock()
    val currentDate = LocalDate.now(ZoneId.systemDefault())
    val currentYear = currentDate.year
    return """
        You are filling a Reported traffic complaint form from one spoken audio recording and an optional attached report photo.
        Transcribe the speech, then return only one strict JSON object.
        Do not include markdown or prose.

        Available complaints:
        $complaints

        Current form and device context:
        $contextBlock

        JSON keys:
        {
          "transcript": string or null,
          "complaint": one available complaint title or null,
          "complaintId": one available complaint ID or null,
          "timeofincident": ISO-8601 incident datetime with timezone or null,
          "occurredAtIso": same value as timeofincident or null,
          "plate": uppercase license plate letters/numbers only, max 8 characters, or null,
          "state": two-letter US plate state, default "NY" only when the speaker implies New York or says no state,
          "address": incident address or null,
          "make": vehicle make, for example "Honda" from "2024 Honda Acura", or null,
          "model": vehicle model, for example "Acura" from "2024 Honda Acura", or null,
          "yearRange": vehicle year or spoken year range, for example "2024" or "2021-2024", or null,
          "description": vehicle description and public-facing incident details or null,
          "notes": extra private details that do not fit another field or null
        }

        Rules:
        - Prefer exact spoken values over guesses.
        - If a report photo is attached, use it as supporting visual context for visible plate text, vehicle details, location clues, and complaint type before producing JSON.
        - If imageVisualContext is present, treat it as a pre-read summary of the attached photo.
        - Spoken values win when audio conflicts with the photo. Do not invent fields from the photo unless they are clearly visible.
        - The current date is $currentDate and the current year is $currentYear. For spoken dates without an explicit year: if the current month is January and the spoken incident month is December, use ${currentYear - 1}. Otherwise, use $currentYear.
        - timeofincident and occurredAtIso must use that month/year rule; do not roll non-December dates back to a previous year.
        - Use current context only when the speaker explicitly refers to it, such as "here", "this location", "current address", "same address", "now", "today", "same time as the photo", "the image time", "same plate", or "keep the state".
        - If the speaker says "here" or "current address", use currentAddress when present; otherwise use imageAddress when present; otherwise use currentCoordinates when present; otherwise use null.
        - If the speaker says "now" or gives a relative time, resolve it against currentDeviceTimeIso.
        - If the speaker says "time from the photo" or "same time as the photo", use imageOccurredAtIso when present.
        - If a field was not spoken, use null.
        - Extract vehicle make, model, and yearRange only when the speaker says them. Do not infer trim, color, or vehicle type into these keys.
        - If the speaker says a phrase like "2024 Honda Acura", set yearRange to "2024", make to "Honda", and model to "Acura".
        - Put any extra information in notes.
        - Pick complaintId only from the available IDs and complaint only from the available titles.
    """.trimIndent()
}

private fun parseVoiceReportGemmaJson(
    jsonText: String,
    complaintOptions: List<ComplaintOption>
): VoiceReportGemmaResult {
    val json = JSONObject(extractFirstJsonObject(jsonText))
    val transcript = json.optNullableString("transcript")
    val transcriptFallback = transcript?.let { generateVoiceReportDraft(it, complaintOptions) }
    val complaintId = resolveVoiceComplaintId(
        rawComplaintId = json.optNullableString("complaintId"),
        rawComplaint = json.optNullableString("complaint"),
        complaintOptions = complaintOptions
    )
        ?: transcriptFallback?.complaintId
    val complaintTitle = complaintOptions.firstOrNull { it.id == complaintId }?.title
    val plate = PlatePatternClassifier.normalizePlateInput(json.optNullableString("plate").orEmpty())
        .take(VoicePlateMaxLength)
        .ifBlank { null }
        ?: transcriptFallback?.plate
    val draft = VoiceReportDraft(
        complaintId = complaintId,
        complaintTitle = complaintTitle,
        plate = plate,
        plateRegion = json.optNullableString("state")?.uppercase(Locale.US)?.take(2)
            ?: transcriptFallback?.plateRegion,
        address = json.optNullableString("address") ?: transcriptFallback?.address,
        occurredAtIso = sanitizeVoiceOccurredAt(
            json.optNullableString("occurredAtIso")
                ?: json.optNullableString("timeofincident")
                ?: json.optNullableString("timeOfIncident")
                ?: json.optNullableString("time_of_incident")
        ) ?: transcriptFallback?.occurredAtIso,
        make = json.optNullableString("make")
            ?: json.optNullableString("vehicleMake")
            ?: json.optNullableString("vehicle_make")
            ?: transcriptFallback?.make,
        model = json.optNullableString("model")
            ?: json.optNullableString("vehicleModel")
            ?: json.optNullableString("vehicle_model")
            ?: transcriptFallback?.model,
        yearRange = json.optNullableString("yearRange")
            ?: json.optNullableString("vehicleYearRange")
            ?: json.optNullableString("vehicle_year_range")
            ?: json.optNullableString("year")
            ?: transcriptFallback?.yearRange,
        description = json.optNullableString("description")
            ?: transcript?.let { extractVoiceSection(it, "description") },
        notes = json.optNullableString("notes") ?: transcriptFallback?.notes
    )
    return VoiceReportGemmaResult(
        transcript = transcript,
        draft = draft
    )
}

private fun extractFirstJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) error("Gemma did not return a JSON object.")
    return text.substring(start, end + 1)
}

private const val VoicePlateMaxLength = 8

private fun resolveVoiceComplaintId(
    rawComplaintId: String?,
    rawComplaint: String?,
    complaintOptions: List<ComplaintOption>
): String? {
    rawComplaintId
        ?.trim()
        ?.takeIf { id -> complaintOptions.any { it.id == id } }
        ?.let { return it }
    val normalizedComplaint = rawComplaint
        ?.lowercase(Locale.US)
        ?.replace(Regex("""[^a-z0-9]+"""), " ")
        ?.trim()
        ?: return null
    if (normalizedComplaint.isBlank()) return null
    return complaintOptions.firstOrNull { option ->
        option.id.equals(rawComplaint, ignoreCase = true) ||
            option.title.lowercase(Locale.US)
                .replace(Regex("""[^a-z0-9]+"""), " ")
                .trim() == normalizedComplaint
    }?.id
        ?: inferVoiceComplaint(normalizedComplaint, complaintOptions)?.id
}

private fun sanitizeVoiceOccurredAt(rawValue: String?): String? {
    val cleaned = rawValue
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: return null
    val instant = runCatching { Instant.parse(cleaned) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(cleaned).toInstant() }.getOrNull()
        }
        ?: return cleaned
    return normalizeVoiceIncidentYear(instant).toString()
}

private fun normalizeVoiceIncidentYear(instant: Instant): Instant {
    val zone = ZoneId.systemDefault()
    val currentDate = LocalDate.now(zone)
    val currentYear = currentDate.year
    val zonedDateTime = instant.atZone(zone)
    val targetYear = if (currentDate.monthValue == 1 && zonedDateTime.monthValue == 12) currentYear - 1 else currentYear
    return zonedDateTime.withYear(targetYear).toInstant()
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key)
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun inferVoiceComplaint(
    transcript: String,
    complaintOptions: List<ComplaintOption>
): ComplaintOption? {
    val normalized = transcript.lowercase(Locale.US)
    val aliases = listOf(
        listOf("blocked bike lane", "bike lane") to listOf("bike", "lane"),
        listOf("blocked crosswalk", "crosswalk") to listOf("crosswalk"),
        listOf("ran red light", "red light", "stop sign") to listOf("red", "light"),
        listOf("parked illegally", "illegal parking") to listOf("park"),
        listOf("reckless driving", "reckless") to listOf("reckless")
    )
    aliases.forEach { (phrases, optionTerms) ->
        if (phrases.any { it in normalized }) {
            complaintOptions.firstOrNull { option ->
                val haystack = "${option.id} ${option.title}".lowercase(Locale.US)
                optionTerms.all { it in haystack }
            }?.let { return it }
        }
    }
    return complaintOptions.firstOrNull { option ->
        val words = option.title
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
        words.isNotEmpty() && words.all { it in normalized }
    }
}

private fun inferVoicePlate(transcript: String): String? {
    val explicit = Regex(
        """\b(?:license\s+plate|plate|tag)\s*(?:is|number|#|:)?\s*([a-z0-9][a-z0-9 -]{1,12}?)(?=\s+(?:state|address|complaint|description|notes|time|at|near)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    val fallback = Regex("""\b[a-z0-9]{5,10}\b""", RegexOption.IGNORE_CASE)
        .findAll(transcript)
        .map { it.value }
        .firstOrNull { token -> token.any(Char::isDigit) && token.any(Char::isLetter) }
    return PlatePatternClassifier.normalizePlateInput(explicit ?: fallback.orEmpty())
        .take(VoicePlateMaxLength)
        .ifBlank { null }
}

private fun inferVoicePlateRegion(transcript: String): String? {
    val normalized = transcript.lowercase(Locale.US)
    val explicit = Regex("""\b(?:state|plate\s+state)\s*(?:is|:)?\s*([a-z]{2}|new york|new jersey|connecticut|pennsylvania)\b""")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    val state = explicit ?: when {
        "new york" in normalized -> "new york"
        "new jersey" in normalized -> "new jersey"
        "connecticut" in normalized -> "connecticut"
        "pennsylvania" in normalized -> "pennsylvania"
        else -> null
    }
    return when (state?.trim()) {
        "new york", "ny" -> "NY"
        "new jersey", "nj" -> "NJ"
        "connecticut", "ct" -> "CT"
        "pennsylvania", "pa" -> "PA"
        else -> state?.uppercase(Locale.US)?.takeIf { it.length == 2 }
    }
}

private fun inferVoiceAddress(transcript: String): String? {
    val match = Regex("""\b(?:address\s+is|address|near|at)\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    val candidate = match.groupValues[1]
        .split(Regex("""\b(?:plate|license\s+plate|state|complaint|description|notes|time|when|occurred)\b""", RegexOption.IGNORE_CASE))
        .firstOrNull()
        ?.trim(' ', ',', '.', ';')
        .orEmpty()
    if (candidate.matches(Regex("""\d{1,2}(:\d{2})?\s*(am|pm).*""", RegexOption.IGNORE_CASE))) return null
    return candidate.takeIf { it.length >= 4 }
}

private fun inferVoiceOccurredAt(transcript: String): String? {
    val normalized = transcript.lowercase(Locale.US)
    val zone = ZoneId.systemDefault()
    if ("now" in normalized || "right now" in normalized) {
        return Instant.now().toString()
    }
    var date = LocalDate.now(zone)
    if ("yesterday" in normalized) {
        date = date.minusDays(1)
    }
    val timeMatch = Regex("""\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE)
        .find(transcript)
    val time = timeMatch?.let { match ->
        val rawHour = match.groupValues[1].toIntOrNull() ?: return@let null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val isPm = match.groupValues.last().lowercase(Locale.US).startsWith("p")
        val hour = when {
            isPm && rawHour < 12 -> rawHour + 12
            !isPm && rawHour == 12 -> 0
            else -> rawHour
        }
        runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
    if (time != null) {
        return date.atTime(time).atZone(zone).toInstant().toString()
    }
    if ("today" in normalized || "yesterday" in normalized) {
        return date.atStartOfDay(zone).toInstant().toString()
    }
    return null
}

private fun inferVoiceVehicleYearRange(transcript: String): String? {
    val match = Regex("""\b((?:19|20)\d{2})(?:\s*(?:-|to|through)\s*((?:19|20)\d{2}))?\b""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    val firstYear = match.groupValues.getOrNull(1).orEmpty()
    val secondYear = match.groupValues.getOrNull(2).orEmpty()
    if (firstYear.isBlank()) return null
    return if (secondYear.isBlank()) firstYear else "$firstYear-$secondYear"
}

private fun inferVoiceVehicleMake(transcript: String): String? {
    val explicit = Regex(
        """\b(?:make|vehicle\s+make)\s*(?:is|:)?\s*([A-Za-z][A-Za-z -]{1,28}?)(?=\s+(?:model|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    return normalizeVoiceVehicleToken(explicit) ?: voiceVehicleMakeModelPhrase(transcript)?.first
}

private fun inferVoiceVehicleModel(transcript: String): String? {
    val explicit = Regex(
        """\b(?:model|vehicle\s+model)\s*(?:is|:)?\s*([A-Za-z0-9][A-Za-z0-9 -]{1,32}?)(?=\s+(?:make|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)""",
        RegexOption.IGNORE_CASE
    ).find(transcript)?.groupValues?.getOrNull(1)
    return normalizeVoiceVehicleToken(explicit) ?: voiceVehicleMakeModelPhrase(transcript)?.second
}

private fun voiceVehicleMakeModelPhrase(transcript: String): Pair<String, String>? {
    val match = Regex(
        """\b(?:19|20)\d{2}(?:\s*(?:-|to|through)\s*(?:19|20)\d{2})?\s+([A-Za-z][A-Za-z-]+)\s+([A-Za-z][A-Za-z0-9-]+)\b""",
        RegexOption.IGNORE_CASE
    ).find(transcript) ?: return null
    val make = normalizeVoiceVehicleToken(match.groupValues.getOrNull(1)) ?: return null
    val model = normalizeVoiceVehicleToken(match.groupValues.getOrNull(2)) ?: return null
    return make to model
}

private fun normalizeVoiceVehicleToken(raw: String?): String? {
    val cleaned = raw
        ?.trim(' ', ',', '.', ';', ':')
        ?.replace(Regex("""\s+"""), " ")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return cleaned
        .split(" ")
        .joinToString(" ") { token ->
            if (token.length <= 4 && token.all { it.isUpperCase() || it.isDigit() }) {
                token
            } else {
                token.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
            }
        }
}

private fun inferVoiceDescription(transcript: String): String? {
    val explicit = extractVoiceSection(transcript, "description")
    return (explicit ?: transcript.trim())
        .take(280)
        .takeIf { it.isNotBlank() }
}

private fun inferVoiceNotes(transcript: String): String? =
    extractVoiceSection(transcript, "notes") ?: extractVoiceSection(transcript, "note")

private fun extractVoiceSection(transcript: String, label: String): String? {
    val match = Regex("""\b$label\s*(?:are|is|:)?\s+(.+)$""", RegexOption.IGNORE_CASE)
        .find(transcript)
        ?: return null
    return match.groupValues[1]
        .split(Regex("""\b(?:plate|license\s+plate|state|complaint|description|notes|address|time|when|occurred)\b""", RegexOption.IGNORE_CASE))
        .firstOrNull()
        ?.trim(' ', ',', '.', ';')
        ?.takeIf { it.isNotBlank() }
}

@Composable
fun AddressSearchScreen(
    state: ComposerUiState,
    onAction: (ComposerAction) -> Unit,
    onBack: () -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.addressQuery, selection = TextRange.Zero))
    }
    val addressProvider = remember(context, state.latitude, state.longitude, state.addressQuery, state.address) {
        reportAddressProviderFor(
            context = context,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.addressQuery.ifBlank { state.address }
        )
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(state.addressQuery) {
        if (state.addressQuery != fieldValue.text) {
            fieldValue = TextFieldValue(state.addressQuery, selection = TextRange.Zero)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Address",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = onOpenMap) {
                Icon(Icons.Outlined.Map, contentDescription = "Pick address on map")
            }
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                onAction(ComposerAction.AddressQueryChanged(next.text))
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            label = { Text(addressProvider.searchLabel) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = if (fieldValue.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            fieldValue = TextFieldValue("")
                            onAction(ComposerAction.AddressQueryChanged(""))
                        }
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear address")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (state.addressQuery.isBlank() && (state.photoAddressSuggestion != null || state.primaryMedia != null)) {
                item {
                    ImageAddressPickerRow(
                        photoAddressSuggestion = state.photoAddressSuggestion,
                        hasMedia = state.primaryMedia != null,
                        onSelected = { suggestion ->
                            onAction(ComposerAction.AddressChosen(suggestion))
                            onBack()
                        }
                    )
                }
            }
            if (state.lookupInFlight) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(addressProvider.searchingLabel)
                    }
                }
            }
            items(state.addressSuggestions, key = { it.label }) { suggestion ->
                AddressSuggestionRow(
                    suggestion = suggestion,
                    onClick = {
                        onAction(ComposerAction.AddressChosen(suggestion))
                        onBack()
                    }
                )
            }
            if (
                !state.lookupInFlight &&
                state.addressSuggestions.isEmpty() &&
                state.addressQuery.isNotBlank()
            ) {
                item {
                    Text(
                        addressProvider.noMatchesLabel,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlateEntryScreen(
    state: ComposerUiState,
    onAction: (ComposerAction) -> Unit,
    onBack: () -> Unit,
    onCandidateSelected: (PlateCandidate) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.plate, selection = TextRange(state.plate.length)))
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(state.plate) {
        if (state.plate != fieldValue.text) {
            fieldValue = TextFieldValue(state.plate, selection = TextRange(state.plate.length))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Plate",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onBack()
                }
            ) {
                Text("Done")
            }
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                val normalized = PlatePatternClassifier.normalizePlateInput(next.text)
                    .take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH)
                val selectionEnd = next.selection.end.coerceIn(0, normalized.length)
                fieldValue = TextFieldValue(normalized, selection = TextRange(selectionEnd))
                onAction(ComposerAction.FieldsChanged(plate = normalized))
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            label = { Text("License plate") },
            trailingIcon = if (fieldValue.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            fieldValue = TextFieldValue("")
                            onAction(ComposerAction.FieldsChanged(plate = ""))
                        }
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear plate")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onBack()
                }
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (state.plateCandidates.isEmpty()) {
                item {
                    Text(
                        "No plate candidates found in the image.",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        "Possible plates",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(state.plateCandidates, key = { it.plate }) { candidate ->
                    PlateCandidateChoiceRow(
                        candidate = candidate,
                        sourceMediaUri = state.primaryMedia?.uri,
                        selected = candidate.plate == state.selectedPlateCandidate,
                        onClick = { onCandidateSelected(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressSuggestionRow(
    suggestion: AddressSuggestion,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null)
            Text(
                suggestion.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ImageAddressPickerRow(
    photoAddressSuggestion: AddressSuggestion?,
    hasMedia: Boolean,
    onSelected: (AddressSuggestion) -> Unit
) {
    if (!hasMedia && photoAddressSuggestion == null) return
    val suggestion = photoAddressSuggestion
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (suggestion != null) Modifier.clickable { onSelected(suggestion) } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (suggestion != null) "Use address from image" else "No image address found",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    suggestion?.label ?: "This photo or video did not include usable GPS metadata.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlateCandidateChoiceRow(
    candidate: PlateCandidate,
    sourceMediaUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) Color(0xFF20B15A) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlateCandidateThumbnail(
                candidate = candidate,
                sourceMediaUri = sourceMediaUri
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(candidate.plate, style = MaterialTheme.typography.titleMedium)
                candidate.plateCorrectionText()?.let { correctionText ->
                    Text(
                        correctionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            Text(
                if (selected) "Selected" else "Use",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color(0xFF20B15A) else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VerifyMediaPanel(
    state: ComposerUiState,
    selectedCandidate: PlateCandidate?,
    modifier: Modifier = Modifier,
    expandPreview: Boolean = false,
    onLaunchMediaPicker: () -> Unit,
    landscapeSubmit: (() -> Unit)? = null,
    onPlateCandidateTapped: (PlateCandidate) -> Unit,
    onPlateCandidateConfirmed: (PlateCandidate) -> Unit,
    onShowPlateCandidates: () -> Unit,
    onRemoveMedia: (SubmissionMedia) -> Unit
) {
    val primaryMedia = state.primaryMedia
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val addMediaLabel = when {
            state.isPhiladelphiaSubmission() && state.primaryMedia == null -> "Add photo"
            state.isPhiladelphiaSubmission() -> "Add another photo"
            state.primaryMedia == null -> "Add photo or video"
            else -> "Add more photos or videos"
        }
        val previewModifier = if (expandPreview) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
        if (primaryMedia != null) {
            PrimaryMediaPreview(
                primaryMedia = primaryMedia,
                extraMedia = state.extraMedia,
                primaryFocalPointX = selectedCandidate?.focalPointX,
                primaryFocalPointY = selectedCandidate?.focalPointY,
                plateCandidates = state.plateCandidates,
                selectedCandidate = selectedCandidate,
                selectedPlate = state.selectedPlateCandidate,
                modifier = previewModifier,
                expand = expandPreview,
                onPlateCandidateTapped = onPlateCandidateTapped,
                onPlateCandidateConfirmed = onPlateCandidateConfirmed,
                onShowPlateCandidates = onShowPlateCandidates,
                onRemoveMedia = onRemoveMedia
            )
        } else {
            PrimaryMediaPlaceholder(
                isError = state.validationErrors.media != null,
                modifier = previewModifier,
                expand = expandPreview,
                onClick = onLaunchMediaPicker
            )
        }
        if (landscapeSubmit != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    addMediaLabel,
                    onClick = onLaunchMediaPicker
                )
                SubmitReportButton(
                    submitting = state.submitting,
                    progress = state.submitProgress,
                    message = state.submitMessage,
                    onClick = landscapeSubmit
                )
            }
        } else {
            SecondaryButton(
                addMediaLabel,
                onClick = onLaunchMediaPicker
            )
        }
        state.validationErrors.media?.let { ValidationMessage(it) }
    }
}

@Composable
fun VerifyFieldsPanel(
    state: ComposerUiState,
    complaintOptions: List<ComplaintOption>,
    isLandscape: Boolean,
    onAction: (ComposerAction) -> Unit,
    onShowComplaintChooser: () -> Unit,
    onShowPlateCandidates: () -> Unit,
    onShowAddressSearch: () -> Unit,
    onShowAddressMap: () -> Unit
) {
    val context = LocalContext.current
    var textEditTarget by remember { mutableStateOf<ReportTextEditTarget?>(null) }
    val showsPhiladelphiaMobilityAccess = state.showsPhiladelphiaMobilityAccessFields(context)
    textEditTarget?.let { target ->
        FullScreenReportTextEditor(
            title = target.title,
            placeholder = target.placeholder,
            initialValue = when (target) {
                ReportTextEditTarget.Description -> state.description
                ReportTextEditTarget.Notes -> state.notes
            },
            onDismiss = { textEditTarget = null },
            onSave = { value ->
                when (target) {
                    ReportTextEditTarget.Description -> onAction(ComposerAction.FieldsChanged(description = value))
                    ReportTextEditTarget.Notes -> onAction(ComposerAction.FieldsChanged(notes = value))
                }
                textEditTarget = null
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReportedSelectionField(
            label = "Complaint",
            value = complaintOptions.firstOrNull { it.id == state.selectedComplaintId }?.title ?: "Select complaint",
            modifier = Modifier.weight(if (isLandscape) 0.9f else 0.9f),
            onClick = onShowComplaintChooser,
            isError = state.validationErrors.complaint != null
        )
        ReportedSelectionField(
            label = "Plate",
            value = state.plate.ifBlank { "Enter plate" },
            modifier = Modifier.weight(if (isLandscape) 0.84f else 0.87f),
            isError = state.validationErrors.plate != null,
            onClick = onShowPlateCandidates,
            trailing = if (state.plateCandidates.isNotEmpty()) {
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
            onSelected = {
                ReportedAnalytics.logStateSelected(it)
                onAction(ComposerAction.FieldsChanged(plateRegion = it))
            },
            modifier = Modifier.weight(if (isLandscape) 0.36f else 0.32f),
            isError = state.validationErrors.plateRegion != null,
            onPickerOpened = {
                ReportedAnalytics.logStateChooserTapped(state.plateRegion)
            }
        )
    }
    FieldErrorRow(
        listOf(
            state.validationErrors.complaint,
            state.validationErrors.plate,
            state.validationErrors.plateRegion
        )
    )
    VehicleLookupStatus(state)
    if (isLandscape) {
        AddressField(
            state = state,
            onShowAddressSearch = onShowAddressSearch,
            onShowAddressMap = onShowAddressMap
        )
        state.validationErrors.address?.let { ValidationMessage(it) }
        OccurredAtField(
            label = "Occurred At",
            isoValue = state.occurredAtIso,
            photoIsoValue = state.photoOccurredAtIso,
            onValueSelected = { onAction(ComposerAction.FieldsChanged(occurredAtIso = it)) },
            isError = state.validationErrors.occurredAt != null,
            onClear = { onAction(ComposerAction.FieldsChanged(occurredAtIso = "")) }
        )
        state.validationErrors.occurredAt?.let { ValidationMessage(it) }
    } else {
        AddressField(
            state = state,
            onShowAddressSearch = onShowAddressSearch,
            onShowAddressMap = onShowAddressMap
        )
        state.validationErrors.address?.let { ValidationMessage(it) }
    }
    if (!isLandscape) {
        OccurredAtField(
            label = "Occurred At",
            isoValue = state.occurredAtIso,
            photoIsoValue = state.photoOccurredAtIso,
            onValueSelected = { onAction(ComposerAction.FieldsChanged(occurredAtIso = it)) },
            isError = state.validationErrors.occurredAt != null,
            onClear = { onAction(ComposerAction.FieldsChanged(occurredAtIso = "")) }
        )
        state.validationErrors.occurredAt?.let { ValidationMessage(it) }
    }
    if (isLandscape) {
        ReportLongTextSelectionField(
            label = ReportTextEditTarget.Description.title,
            value = state.description,
            placeholder = ReportTextEditTarget.Description.placeholder,
            onClick = { textEditTarget = ReportTextEditTarget.Description }
        )
        if (showsPhiladelphiaMobilityAccess) {
            PhiladelphiaMobilityAccessFields(
                state = state,
                isLandscape = true,
                onAction = onAction
            )
        }
        ReportLongTextSelectionField(
            label = ReportTextEditTarget.Notes.title,
            value = state.notes,
            placeholder = ReportTextEditTarget.Notes.placeholder,
            onClick = { textEditTarget = ReportTextEditTarget.Notes }
        )
    } else {
        ReportLongTextSelectionField(
            label = ReportTextEditTarget.Description.title,
            value = state.description,
            placeholder = ReportTextEditTarget.Description.placeholder,
            onClick = { textEditTarget = ReportTextEditTarget.Description }
        )
        if (showsPhiladelphiaMobilityAccess) {
            PhiladelphiaMobilityAccessFields(
                state = state,
                isLandscape = false,
                onAction = onAction
            )
        }
        ReportLongTextSelectionField(
            label = ReportTextEditTarget.Notes.title,
            value = state.notes,
            placeholder = ReportTextEditTarget.Notes.placeholder,
            onClick = { textEditTarget = ReportTextEditTarget.Notes }
        )
    }
}

private enum class ReportTextEditTarget(
    val title: String,
    val placeholder: String
) {
    Description(
        title = "Description (public facing)",
        placeholder = "Describe what happened"
    ),
    Notes(
        title = "Notes (for your records)",
        placeholder = "Add private notes"
    )
}

@Composable
private fun ReportLongTextSelectionField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ReportedSelectionField(
        label = label,
        value = value.ifBlank { placeholder },
        modifier = modifier,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun PhiladelphiaMobilityAccessFields(
    state: ComposerUiState,
    isLandscape: Boolean,
    onAction: (ComposerAction) -> Unit
) {
    val details = state.philadelphiaMobilityAccessDetails
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Philadelphia mobility access",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportedField(
                    label = "Block Number",
                    value = details.blockNumber,
                    onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(blockNumber = it)) },
                    modifier = Modifier.weight(0.8f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                ReportedField(
                    label = "Street Name",
                    value = details.streetName,
                    onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(streetName = it)) },
                    modifier = Modifier.weight(1.4f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                ReportedOptionsField(
                    label = "Zip Code",
                    value = details.zipCode,
                    options = CityReportingRules.philadelphiaZipCodes.toList(),
                    onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(zipCode = it)) },
                    modifier = Modifier.weight(0.8f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportedField(
                    label = "Block Number",
                    value = details.blockNumber,
                    onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(blockNumber = it)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                ReportedOptionsField(
                    label = "Zip Code",
                    value = details.zipCode,
                    options = CityReportingRules.philadelphiaZipCodes.toList(),
                    onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(zipCode = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            ReportedField(
                label = "Street Name",
                value = details.streetName,
                onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(streetName = it)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportedAutocompleteField(
                label = "Vehicle Make",
                value = details.vehicleMake,
                options = PhiladelphiaMobilityAccessCatalogs.vehicleMakes,
                onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(vehicleMake = it)) },
                modifier = Modifier.weight(1f)
            )
            ReportedAutocompleteField(
                label = "Vehicle Model",
                value = details.vehicleModel,
                options = listOfNotNull(state.vehicleDescription?.model)
                    .plus(PhiladelphiaMobilityAccessCatalogs.vehicleModels)
                    .distinctBy { it.lowercase(Locale.US) },
                onValueChange = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(vehicleModel = it)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportedOptionsField(
                label = "Body Style",
                value = details.bodyStyle,
                options = PhiladelphiaMobilityAccessCatalogs.bodyStyles,
                onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(bodyStyle = it)) },
                modifier = Modifier.weight(1f)
            )
            ReportedOptionsField(
                label = "Vehicle Color",
                value = details.vehicleColor,
                options = PhiladelphiaMobilityAccessCatalogs.vehicleColors,
                onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(vehicleColor = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        ReportedOptionsField(
            label = "Violation Observed",
            value = details.violationObserved,
            options = PhiladelphiaMobilityAccessCatalogs.violationObservedOptions,
            onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(violationObserved = it)) }
        )
        ReportedOptionsField(
            label = "How Frequently Does This Occur?",
            value = details.frequency,
            options = PhiladelphiaMobilityAccessCatalogs.frequencyOptions,
            onSelected = { onAction(ComposerAction.PhiladelphiaMobilityAccessChanged(frequency = it)) }
        )
    }
}

@Composable
private fun ReportedAutocompleteField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(value, options) {
        val query = value.trim()
        val matches = if (query.isBlank()) {
            options
        } else {
            options.filter { it.contains(query, ignoreCase = true) }
        }
        matches.distinctBy { it.lowercase(Locale.US) }.take(8)
    }
    Box(modifier = modifier) {
        ReportedField(
            label = label,
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) expanded = true
                },
            keyboardOptions = keyboardOptions,
            trailingContent = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
        DropdownMenu(
            expanded = focused && expanded && filteredOptions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportedOptionsField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                onSelected(option)
                                showOptions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                option,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptions = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    ReportedSelectionField(
        label = label,
        value = value.ifBlank { "Select" },
        modifier = modifier,
        onClick = { showOptions = true },
        trailing = {
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun VehicleLookupStatus(state: ComposerUiState) {
    val details = state.vehicleLookupDetails
    val statusText = when {
        state.vehicleLookupInFlight -> "Looking up vehicle details for ${state.plate} in ${state.plateRegion}…"
        details != null -> details.summary
        else -> state.vehicleLookupMessage
    } ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.vehicleLookupInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (details != null) {
                    Text(
                        text = "Vehicle details from LookupAPlate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun ComposerUiState.showsPhiladelphiaMobilityAccessFields(context: Context): Boolean =
    reportAddressProviderFor(
        context = context,
        latitude = latitude,
        longitude = longitude,
        address = addressQuery.ifBlank { address }
    ) == ReportAddressProvider.Philadelphia

@Composable
private fun FullScreenReportTextEditor(
    title: String,
    placeholder: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close editor")
                    }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { onSave(text) }) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text(placeholder) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 12,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        }
    }
}

@Composable
private fun AddressField(
    state: ComposerUiState,
    onShowAddressSearch: () -> Unit,
    onShowAddressMap: () -> Unit
) {
    ReportedSelectionField(
        label = "Address",
        value = state.addressQuery.ifBlank { "Enter address" },
        isError = state.validationErrors.address != null,
        onClick = onShowAddressSearch,
        trailing = {
            IconButton(onClick = onShowAddressMap) {
                Icon(Icons.Outlined.Map, contentDescription = "Pick address on map")
            }
        }
    )
}

@Composable
private fun SubmitReportButton(
    submitting: Boolean,
    progress: Float?,
    message: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false
) {
    val buttonShape = if (edgeToEdge) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(8.dp)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrimaryButton(
            "Submit Report",
            onClick = onClick,
            enabled = !submitting,
            shape = buttonShape,
            contentPadding = if (edgeToEdge) {
                PaddingValues(top = 26.dp, bottom = 26.dp)
            } else {
                PaddingValues(vertical = 16.dp)
            },
            textSize = if (edgeToEdge) 20.sp else 16.sp
        )
        if (submitting) {
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = message ?: "Submitting report",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
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
    onAnimationFinished: () -> Unit = {},
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
                    onAnimationFinished = onAnimationFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = complaintTileTitleSize(option.title)
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = complaintTileTitleSize(option.title)
                    ),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ComplaintTileArt(
    option: ComplaintOption,
    animate: Boolean,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val isRanRedLightOrStopSign = option.isRanRedLightOrStopSign()
    val artBackground = if (isRanRedLightOrStopSign) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val artModifier = modifier
        .background(artBackground, shape)
        .clip(shape)
    val lottieContentScale = if (isRanRedLightOrStopSign) ContentScale.Fit else ContentScale.Crop
    if (option.lottieAssetPath != null) {
        val composition by rememberLottieComposition(LottieCompositionSpec.Asset(option.lottieAssetPath))
        val progress by animateLottieCompositionAsState(
            composition = composition,
            isPlaying = animate,
            iterations = 1,
            restartOnPlay = true
        )
        var notifiedFinished by remember(option.id, animate) { mutableStateOf(false) }
        LaunchedEffect(animate, progress) {
            if (!animate) {
                notifiedFinished = false
                return@LaunchedEffect
            }
            if (progress >= 0.999f && !notifiedFinished) {
                notifiedFinished = true
                onAnimationFinished()
            }
        }
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { if (animate) progress else 0f },
                modifier = artModifier,
                contentScale = lottieContentScale,
                alignment = Alignment.Center
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
fun PrimaryMediaPreview(
    primaryMedia: SubmissionMedia,
    extraMedia: List<SubmissionMedia>,
    primaryFocalPointX: Float? = null,
    primaryFocalPointY: Float? = null,
    plateCandidates: List<PlateCandidate> = emptyList(),
    selectedCandidate: PlateCandidate? = null,
    selectedPlate: String? = null,
    modifier: Modifier = Modifier,
    expand: Boolean = false,
    showRemoveButton: Boolean = true,
    onPlateCandidateTapped: (PlateCandidate) -> Unit,
    onPlateCandidateConfirmed: (PlateCandidate) -> Unit,
    onShowPlateCandidates: () -> Unit,
    onRemoveMedia: (SubmissionMedia) -> Unit
) {
    val mediaItems = remember(primaryMedia, extraMedia) { listOf(primaryMedia) + extraMedia }
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    var fullScreenMedia by remember { mutableStateOf<SubmissionMedia?>(null) }
    var fullScreenVideoCandidate by remember { mutableStateOf<PlateCandidate?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        OutlinedCard(
            modifier = if (expand) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
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
                modifier = if (expand) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = if (expand) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth()
                    }
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
                        modifier = if (expand) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        }
                    ) {
                        if (media.isVideo) {
                            var videoFrameImageSize by remember(media.uri, selectedCandidate?.videoFramePreviewUri) {
                                mutableStateOf<Size?>(null)
                            }
                            val videoPreviewCandidate = selectedCandidate
                                ?.takeIf { page == 0 && it.videoFramePreviewUri != null }
                            if (videoPreviewCandidate?.videoFramePreviewUri != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                        .clickable { fullScreenVideoCandidate = videoPreviewCandidate }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(videoPreviewCandidate.videoFramePreviewUri)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = "Selected video frame",
                                        onSuccess = { state ->
                                            val intrinsicSize = state.painter.intrinsicSize
                                            if (
                                                intrinsicSize.width.isFinite() &&
                                                intrinsicSize.height.isFinite() &&
                                                intrinsicSize.width > 0f &&
                                                intrinsicSize.height > 0f
                                            ) {
                                                videoFrameImageSize = intrinsicSize
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    LicensePlateBoundsOverlay(
                                        candidates = listOf(videoPreviewCandidate),
                                        selectedPlate = videoPreviewCandidate.plate,
                                        imageSize = videoFrameImageSize,
                                        alignment = Alignment.Center,
                                        contentScale = ContentScale.Fit,
                                        onCandidateTapped = onPlateCandidateTapped,
                                        onEmptyTap = { fullScreenVideoCandidate = videoPreviewCandidate }
                                    )
                                }
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
                                    imageSize = plateCandidates.detectionSourceSize() ?: imageSize,
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
                onPlateCandidateConfirmed = onPlateCandidateConfirmed,
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
        if (showRemoveButton && currentMedia != null) {
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
    modifier: Modifier = Modifier,
    expand: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier
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
            modifier = if (expand) {
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            },
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
    val candidateSourceSize = candidatesSourceSize(candidate)
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

private fun List<PlateCandidate>.detectionSourceSize(): Size? =
    firstNotNullOfOrNull(::candidatesSourceSize)

private fun candidatesSourceSize(candidate: PlateCandidate): Size? {
    val width = candidate.sourceImageWidth?.takeIf { it > 0 } ?: return null
    val height = candidate.sourceImageHeight?.takeIf { it > 0 } ?: return null
    return Size(width.toFloat(), height.toFloat())
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
    var currentPositionMs by remember(player) { mutableLongStateOf(candidate.videoFrameTimeMs ?: 0L) }
    LaunchedEffect(player) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            delay(100)
        }
    }
    val shouldShowDetectionOverlay = abs(currentPositionMs - (candidate.videoFrameTimeMs ?: 0L)) <= 750L

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
                if (shouldShowDetectionOverlay) {
                    PlateCandidateOverlay(
                        candidates = listOf(candidate),
                        selectedPlate = candidate.plate,
                        onCandidateSelected = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .zIndex(10f)
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 12.dp, end = 12.dp)
                    .size(52.dp),
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
    onPlateCandidateConfirmed: (PlateCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember(media.uri) { mutableStateOf(1f) }
    var offsetX by remember(media.uri) { mutableStateOf(0f) }
    var offsetY by remember(media.uri) { mutableStateOf(0f) }
    var imageSize by remember(media.uri) { mutableStateOf<Size?>(null) }
    var pendingCandidate by remember(media.uri) { mutableStateOf<PlateCandidate?>(null) }

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
                        inputEnabled = true,
                        onCandidateTapped = { pendingCandidate = it },
                        onEmptyTap = {}
                    )
                }
            }
            pendingCandidate?.let { candidate ->
                AlertDialog(
                    onDismissRequest = { pendingCandidate = null },
                    title = { Text("Use this plate?") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PlateCandidateThumbnail(
                                candidate = candidate,
                                sourceMediaUri = media.uri
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(candidate.plate, style = MaterialTheme.typography.titleLarge)
                                candidate.plateCorrectionText()?.let { correctionText ->
                                    Text(
                                        correctionText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    "${(candidate.confidence * 100).toInt()}% confidence",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                candidate.stateClassifierText()?.let { classifierText ->
                                    Text(
                                        classifierText,
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
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onPlateCandidateConfirmed(candidate)
                                pendingCandidate = null
                            }
                        ) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingCandidate = null }) {
                            Text("No")
                        }
                    }
                )
            }
            Surface(
                modifier = Modifier
                    .zIndex(10f)
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddressMapSheet(
    initialLatLng: LatLng,
    initialAddress: String,
    photoAddressSuggestion: AddressSuggestion?,
    onDismiss: () -> Unit,
    onLocationSettled: (AddressSuggestion) -> Unit
) {
    val context = LocalContext.current
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
        val suggestion = reverseGeocodeAddress(context, latLng.latitude, latLng.longitude)
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

    fun usePhotoAddress(suggestion: AddressSuggestion) {
        val latLng = LatLng(suggestion.latitude, suggestion.longitude)
        resolvedAddress = suggestion.label
        pendingSuggestion = suggestion
        addressCache[addressCacheKey(latLng)] = suggestion
        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16f)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val sheetIsLandscape = maxWidth > maxHeight
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(if (sheetIsLandscape) 0.75f else 1f)
                        .fillMaxHeight(0.96f),
                    shape = RoundedCornerShape(22.dp),
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
                                    photoAddressSuggestion = photoAddressSuggestion,
                                    onPhotoAddressSelected = ::usePhotoAddress,
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
                                ImageAddressPickerRow(
                                    photoAddressSuggestion = photoAddressSuggestion,
                                    hasMedia = photoAddressSuggestion != null,
                                    onSelected = ::usePhotoAddress
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
    photoAddressSuggestion: AddressSuggestion?,
    onPhotoAddressSelected: (AddressSuggestion) -> Unit,
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
            ImageAddressPickerRow(
                photoAddressSuggestion = photoAddressSuggestion,
                hasMedia = photoAddressSuggestion != null,
                onSelected = onPhotoAddressSelected
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(
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
fun PlateCandidatePickerDialog(
    candidates: List<PlateCandidate>,
    selectedPlate: String?,
    sourceMediaUri: String? = null,
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
                            PlateCandidateThumbnail(
                                candidate = candidate,
                                sourceMediaUri = sourceMediaUri
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(candidate.plate, style = MaterialTheme.typography.titleMedium)
                                candidate.plateCorrectionText()?.let { correctionText ->
                                    Text(
                                        correctionText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
private fun PlateCandidateThumbnail(
    candidate: PlateCandidate,
    sourceMediaUri: String? = null
) {
    val fallbackUri = candidate.videoFramePreviewUri ?: sourceMediaUri
    var showFallback by remember(candidate.thumbnailUri, fallbackUri) {
        mutableStateOf(candidate.thumbnailUri.isNullOrBlank())
    }
    var imageFailed by remember(candidate.thumbnailUri, fallbackUri) { mutableStateOf(false) }
    val imageUri = if (showFallback) fallbackUri else candidate.thumbnailUri
    val imageAlignment = if (showFallback && imageUri == sourceMediaUri) {
        candidate.thumbnailSourceAlignment()
    } else {
        Alignment.Center
    }
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri != null && !imageFailed) {
            AsyncImage(
                model = imageUri,
                contentDescription = candidate.plate,
                onError = {
                    if (!showFallback && fallbackUri != null) {
                        showFallback = true
                        imageFailed = false
                    } else {
                        imageFailed = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = imageAlignment
            )
        } else {
            Text("No image", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun PlateCandidate.thumbnailSourceAlignment(): Alignment {
    val x = focalPointX ?: 0.5f
    val y = focalPointY ?: 0.5f
    return BiasAlignment(
        horizontalBias = (x * 2f - 1f).coerceIn(-1f, 1f),
        verticalBias = (y * 2f - 1f).coerceIn(-1f, 1f)
    )
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

private fun PlateCandidate.plateCorrectionText(): String? {
    val raw = rawPlateText?.takeIf { wasPlateCorrected && it != plate } ?: return null
    return "Corrected from OCR: $raw"
}
