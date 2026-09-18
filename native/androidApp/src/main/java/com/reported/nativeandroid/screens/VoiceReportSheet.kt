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
import androidx.compose.material3.rememberBottomSheetState
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
internal fun VoiceReportAssistantSheet(
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
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
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
