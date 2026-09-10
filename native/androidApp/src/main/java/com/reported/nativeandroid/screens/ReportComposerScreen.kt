package com.reported.nativeandroid.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.reported.nativeandroid.app.ComposerAction
import com.reported.nativeandroid.app.ComposerEvent
import com.reported.nativeandroid.app.ComposerViewModel
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.app.PhiladelphiaSubmissionVideoMessage
import com.reported.nativeandroid.app.SharedMediaRequest
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.SubmissionStage
import com.reported.nativeandroid.app.isPhiladelphiaSubmission
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.ai.ReportedAiModelStore
import com.reported.nativeandroid.analytics.ReportedAnalytics
import com.reported.nativeandroid.media.MediaScannerScheduler
import com.reported.nativeandroid.media.MediaScannerSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal const val ROTATED_PLATE_OVERLAY_THRESHOLD_DEGREES = 4f
internal const val REPORT_TUTORIAL_PREFS = "reported.report.tutorial"
internal const val REPORT_TUTORIAL_SEEN_KEY = "new_report_tutorial_seen"
internal val ReportedAiFabSize = 58.dp
internal val ReportedAiFabVerifyBottomOffset = 76.dp
internal val ReportedAiFabDefaultBottomOffset = 24.dp

internal fun hasSeenReportTutorial(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(REPORT_TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .getBoolean(REPORT_TUTORIAL_SEEN_KEY, false)

internal fun markReportTutorialSeen(context: Context) {
    context.applicationContext
        .getSharedPreferences(REPORT_TUTORIAL_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(REPORT_TUTORIAL_SEEN_KEY, true)
        .apply()
}

internal fun hasMediaLocationPermission(context: Context): Boolean =
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
                ComposerEvent.RequireLogin -> onRequireLogin {
                    vm.onAction(ComposerAction.SubmitPressed)
                }
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

    ReportComposerOverlays(
        state = state,
        complaintOptions = complaintOptions,
        activeAnimatedOptionIndex = activeAnimatedOptionIndex,
        showReportTutorial = showReportTutorial,
        tutorialScannerEnabled = tutorialScannerEnabled,
        tutorialNotificationsEnabled = tutorialNotificationsEnabled,
        showVoiceAssistSheet = showVoiceAssistSheet,
        hasVoiceMicrophonePermission = hasVoiceMicrophonePermission,
        voiceTranscript = voiceTranscript,
        voiceDraft = voiceDraft,
        voiceRecording = voiceRecording,
        voiceProcessing = voiceProcessing,
        voiceAmplitude = voiceAmplitude,
        voiceModelInstalled = voiceModelInstalled,
        voiceModelDownloading = voiceModelDownloading,
        voiceModelDownloadProgress = voiceModelDownloadProgress,
        voiceAccelerationMessage = voiceAccelerationMessage,
        voiceError = voiceError,
        showComplaintChooser = showComplaintChooser,
        showDiscardDialog = showDiscardDialog,
        showAddressMap = showAddressMap,
        pendingPlateCandidate = pendingPlateCandidate,
        onAction = vm::onAction,
        onAdvanceAnimatedComplaint = ::advanceAnimatedComplaint,
        onTutorialScannerEnabledChange = { tutorialScannerEnabled = it },
        onTutorialNotificationsEnabledChange = { tutorialNotificationsEnabled = it },
        onRequestMediaLocationPermission = {
            tutorialMetadataPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        },
        onTutorialSkip = {
            markReportTutorialSeen(context)
            showReportTutorial = false
        },
        onTutorialComplete = ::completeReportTutorialFromButton,
        onVoicePermission = { voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onVoiceModelDownload = ::downloadVoiceModel,
        onVoiceTalk = ::startVoiceCapture,
        onVoiceStop = ::stopVoiceCapture,
        onVoiceClear = {
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
        onVoiceApply = ::applyVoiceDraft,
        onVoiceDismiss = {
            voiceImageContextJob?.cancel()
            voiceImageContextJob = null
            voiceAudioRecorder.cancel()
            voiceRecording = false
            voiceAmplitude = 0f
            showVoiceAssistSheet = false
        },
        onProcessVideo = { startVideoScan(videoMedia = it, confirmProcessing = true) },
        onComplaintChooserDismiss = { showComplaintChooser = false },
        onComplaintSelected = { complaintId ->
            ReportedAnalytics.logComplaintSelected(complaintId, "verify")
            vm.onAction(ComposerAction.SelectedComplaintChanged(complaintId))
            showComplaintChooser = false
        },
        onDiscardDismiss = { showDiscardDialog = false },
        onDiscardConfirm = {
            showDiscardDialog = false
            vm.onAction(ComposerAction.DiscardDraftConfirmed)
        },
        onAddressMapDismiss = { showAddressMap = false },
        onPlateCandidateDismiss = { pendingPlateCandidate = null },
        onPlateCandidateConfirmed = {
            vm.onAction(ComposerAction.PlateCandidateChosen(it))
            pendingPlateCandidate = null
        }
    )


    ReportComposerStageContent(
        state = state,
        complaintOptions = complaintOptions,
        activeAnimatedOptionIndex = activeAnimatedOptionIndex,
        isAuthorized = isAuthorized,
        isKeyboardVisible = isKeyboardVisible,
        isScreenLandscape = isScreenLandscape,
        showAddressSearchScreen = showAddressSearchScreen,
        showPlateCandidates = showPlateCandidates,
        detectionProgressMinimized = detectionProgressMinimized,
        hasDraftContent = hasDraftContent,
        verifyListState = verifyListState,
        onOpenMenu = onOpenMenu,
        onAction = vm::onAction,
        onAddressSearchClosed = {
            showAddressSearchScreen = false
            vm.onAction(ComposerAction.AddressSuggestionsChanged(emptyList()))
        },
        onShowAddressSearch = { showAddressSearchScreen = true },
        onShowAddressMap = {
            showAddressSearchScreen = false
            showAddressMap = true
        },
        onPlateCandidatesClosed = { showPlateCandidates = false },
        onChooseMedia = { complaintId ->
            pendingMultipleSelection = false
            pendingMediaForCurrentReport = false
            pendingComplaintId = complaintId
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        },
        onLaunchMediaPicker = {
            pendingComplaintId = state.selectedComplaintId
            pendingMultipleSelection = state.primaryMedia != null
            pendingMediaForCurrentReport = state.primaryMedia == null
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        },
        onClearRequested = { showDiscardDialog = true },
        onAdvanceAnimatedComplaint = ::advanceAnimatedComplaint,
        onOpenComplaintChooser = ::openComplaintChooser,
        onOpenPlateChooser = ::openPlateChooser,
        onPlateCandidateTapped = { pendingPlateCandidate = it },
        onLandscapeLayoutChanged = { isLandscapeLayout = it },
        onDetectionProgressExpanded = { detectionProgressMinimized = false },
        onOpenVoiceAssistant = ::openVoiceAssistant
    )
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
