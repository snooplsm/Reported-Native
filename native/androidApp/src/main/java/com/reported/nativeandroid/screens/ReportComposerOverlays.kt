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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ReportComposerOverlays(
    state: ComposerUiState,
    complaintOptions: List<ComplaintOption>,
    activeAnimatedOptionIndex: Int,
    showReportTutorial: Boolean,
    tutorialScannerEnabled: Boolean,
    tutorialNotificationsEnabled: Boolean,
    showVoiceAssistSheet: Boolean,
    hasVoiceMicrophonePermission: Boolean,
    voiceTranscript: String,
    voiceDraft: VoiceReportDraft?,
    voiceRecording: Boolean,
    voiceProcessing: Boolean,
    voiceAmplitude: Float,
    voiceModelInstalled: Boolean,
    voiceModelDownloading: Boolean,
    voiceModelDownloadProgress: Float?,
    voiceAccelerationMessage: String,
    voiceError: String?,
    showComplaintChooser: Boolean,
    showDiscardDialog: Boolean,
    showAddressMap: Boolean,
    pendingPlateCandidate: PlateCandidate?,
    onAction: (ComposerAction) -> Unit,
    onAdvanceAnimatedComplaint: (Int) -> Unit,
    onTutorialScannerEnabledChange: (Boolean) -> Unit,
    onTutorialNotificationsEnabledChange: (Boolean) -> Unit,
    onRequestMediaLocationPermission: () -> Unit,
    onTutorialSkip: () -> Unit,
    onTutorialComplete: () -> Unit,
    onVoicePermission: () -> Unit,
    onVoiceModelDownload: () -> Unit,
    onVoiceTalk: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceClear: () -> Unit,
    onVoiceApply: (VoiceReportDraft) -> Unit,
    onVoiceDismiss: () -> Unit,
    onProcessVideo: (SubmissionMedia) -> Unit,
    onComplaintChooserDismiss: () -> Unit,
    onComplaintSelected: (String) -> Unit,
    onDiscardDismiss: () -> Unit,
    onDiscardConfirm: () -> Unit,
    onAddressMapDismiss: () -> Unit,
    onPlateCandidateDismiss: () -> Unit,
    onPlateCandidateConfirmed: (PlateCandidate) -> Unit
) {
    val context = LocalContext.current
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
                                    onAdvanceAnimatedComplaint(complaintOptions.indexOfFirst { it.id == option.id })
                                },
                                onClick = {
                                    ReportedAnalytics.logComplaintSelected(option.id, "pending_media")
                                    onAction(ComposerAction.PendingComplaintConfirmed(option.id))
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
            onScannerEnabledChange = { onTutorialScannerEnabledChange(it) },
            notificationsEnabled = tutorialNotificationsEnabled,
            onNotificationsEnabledChange = { onTutorialNotificationsEnabledChange(it) },
            onRequestMediaLocationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasMediaLocationPermission(context)) {
                    onRequestMediaLocationPermission()
                }
            },
            onSkip = {
                onTutorialSkip()
            },
            onComplete = onTutorialComplete
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
                onVoicePermission()
            },
            onDownloadModel = onVoiceModelDownload,
            onTalk = onVoiceTalk,
            onStop = onVoiceStop,
            onClear = onVoiceClear,
            onApply = onVoiceApply,
            onDismiss = onVoiceDismiss
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
                    onProcessVideo(pendingVideoMedia)
                }) { Text("Process") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ComposerAction.VideoProcessingDecision(false)) }) { Text("Skip") }
            }
        )
    }

    if (showComplaintChooser) {
        AlertDialog(
            onDismissRequest = { onComplaintChooserDismiss() },
            title = { Text("Change complaint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    complaintOptions.forEach { option ->
                        Text(
                            text = option.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onComplaintSelected(option.id)
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onComplaintChooserDismiss() }) {
                    Text("Cancel")
                }
            }
        )
    }

    state.plateCorrectionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { onAction(ComposerAction.PlateCorrectionDismissed) },
            title = { Text("Fix plate format?") },
            text = {
                Text(
                    "This looks like a ${prompt.label} plate. Use ${prompt.suggestedPlate} instead of ${prompt.rawPlate}?"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAction(ComposerAction.PlateCorrectionKept) }) {
                        Text("Keep")
                    }
                    TextButton(onClick = { onAction(ComposerAction.PlateCorrectionAccepted) }) {
                        Text("Use ${prompt.suggestedPlate}")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ComposerAction.PlateCorrectionDismissed) }) {
                    Text("Edit")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { onDiscardDismiss() },
            title = { Text("Discard report?") },
            text = { Text("This will clear the current report and remove any saved draft for it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDiscardConfirm()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDiscardDismiss() }) {
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
            onDismiss = { onAddressMapDismiss() },
            onLocationSettled = { suggestion ->
                onAction(ComposerAction.AddressChosen(suggestion))
            }
        )
    }

    pendingPlateCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { onPlateCandidateDismiss() },
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
                        onPlateCandidateConfirmed(candidate)
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { onPlateCandidateDismiss() }) {
                    Text("No")
                }
            }
        )
    }

}
