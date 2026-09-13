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

@Composable
internal fun ReportComposerStageContent(
    state: ComposerUiState,
    complaintOptions: List<ComplaintOption>,

    isAuthorized: Boolean,
    isKeyboardVisible: Boolean,
    isScreenLandscape: Boolean,
    showAddressSearchScreen: Boolean,
    showPlateCandidates: Boolean,
    detectionProgressMinimized: Boolean,
    hasDraftContent: Boolean,
    verifyListState: androidx.compose.foundation.lazy.LazyListState,
    onOpenMenu: () -> Unit,
    onAction: (ComposerAction) -> Unit,
    onAddressSearchClosed: () -> Unit,
    onShowAddressSearch: () -> Unit,
    onShowAddressMap: () -> Unit,
    onPlateCandidatesClosed: () -> Unit,
    onChooseMedia: (String?) -> Unit,
    onLaunchMediaPicker: () -> Unit,
    onClearRequested: () -> Unit,

    onOpenComplaintChooser: (String) -> Unit,
    onOpenPlateChooser: () -> Unit,
    onPlateCandidateTapped: (PlateCandidate) -> Unit,
    onLandscapeLayoutChanged: (Boolean) -> Unit,
    onDetectionProgressExpanded: () -> Unit,
    onOpenVoiceAssistant: () -> Unit
) {
    if (showAddressSearchScreen) {
        AddressSearchScreen(
            state = state,
            onAction = onAction,
            onBack = {
                onAddressSearchClosed()
                onAction(ComposerAction.AddressSuggestionsChanged(emptyList()))
            },
            onOpenMap = {
                onAddressSearchClosed()
                onShowAddressMap()
            }
        )
    } else if (showPlateCandidates) {
        PlateEntryScreen(
            state = state,
            onAction = onAction,
            onBack = { onPlateCandidatesClosed() },
            onCandidateSelected = { candidate ->
                onAction(ComposerAction.PlateCandidateChosen(candidate))
                onPlateCandidatesClosed()
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
                        onClear = { onClearRequested() }
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
                                onClear = { onClearRequested() }
                            )
                            Text(
                                "Add Photo",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                            Text(
                                "Choose a complaint or add a photo.",
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
                                                onChooseMedia(selectedComplaintOption.id)
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

                                                showImage = state.showComplaintImages,
                                                onClick = {
                                                    ReportedAnalytics.logComplaintSelected(option.id, "pick_media")
                                                    onChooseMedia(option.id)
                                                }
                                            )
                                        }
                                        if (row.size < 3) {
                                            UploadTile(
                                                modifier = Modifier.weight(1f),
                                                showImage = state.showComplaintImages,
                                                onClick = {
                                                    onChooseMedia(null)
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
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Add Photo",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    "Choose a complaint or add a photo.",
                                    style = MaterialTheme.typography.bodySmall,
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
                                            onChooseMedia(selectedComplaintOption.id)
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

                                            showImage = state.showComplaintImages,
                                            onClick = {
                                                ReportedAnalytics.logComplaintSelected(option.id, "pick_media")
                                                onChooseMedia(option.id)
                                            }
                                        )
                                    }
                                    if (row.size == 1) {
                                        UploadTile(
                                            modifier = Modifier.weight(1f),
                                            showImage = state.showComplaintImages,
                                            onClick = {
                                                onChooseMedia(null)
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
                val launchMediaPicker = onLaunchMediaPicker
                val submitReport = {
                    val mediaToSubmitMillis = state.firstMediaAddedElapsedRealtimeMs
                        ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
                    ReportedAnalytics.logSubmitReportTapped(
                        stage = if (state.stage == SubmissionStage.VERIFY) "verify" else "pick_media",
                        isAuthorized = isAuthorized,
                        mediaToSubmitMillis = mediaToSubmitMillis
                    )
                    onAction(ComposerAction.SubmitRequested(isAuthorized))
                }

                val reportedAiFabVisible = state.primaryMedia != null && !isKeyboardVisible
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > maxHeight
                    val reportedAiFabSpacer = if (reportedAiFabVisible) ReportedAiFabSize else 0.dp
                    LaunchedEffect(isLandscape) {
                        onLandscapeLayoutChanged(isLandscape)
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
                                        onClear = { onClearRequested() }
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
                                        onPlateCandidateTapped = { onPlateCandidateTapped(it) },
                                        onPlateCandidateConfirmed = { onAction(ComposerAction.PlateCandidateChosen(it)) },
                                        onShowPlateCandidates = onOpenPlateChooser,
                                        onRemoveMedia = { onAction(ComposerAction.MediaRemoved(it)) }
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
                                                    onAction = onAction,
                                                    onShowComplaintChooser = { onOpenComplaintChooser("verify") },
                                                    onShowPlateCandidates = onOpenPlateChooser,
                                                    onShowAddressSearch = onShowAddressSearch,
                                                    onShowAddressMap = { onShowAddressMap() }
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
                                                onClick = { onClearRequested() },
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
                                            onPlateCandidateTapped = { onPlateCandidateTapped(it) },
                                            onPlateCandidateConfirmed = { onAction(ComposerAction.PlateCandidateChosen(it)) },
                                            onShowPlateCandidates = onOpenPlateChooser,
                                            onRemoveMedia = { onAction(ComposerAction.MediaRemoved(it)) }
                                        )
                                        VerifyFieldsPanel(
                                            state = state,
                                            complaintOptions = complaintOptions,
                                            isLandscape = false,
                                            onAction = onAction,
                                            onShowComplaintChooser = { onOpenComplaintChooser("verify") },
                                            onShowPlateCandidates = onOpenPlateChooser,
                                            onShowAddressSearch = onShowAddressSearch,
                                            onShowAddressMap = { onShowAddressMap() }
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
                                onClick = { onDetectionProgressExpanded() }
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
                                    IconButton(onClick = { onAction(ComposerAction.DetectionResultDismissed) }) {
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
                    onClick = onOpenVoiceAssistant,
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

}
