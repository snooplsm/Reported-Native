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
internal fun VoiceMinimizedRecordingControl(
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
internal fun VoiceCaptureControl(
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
internal fun VoiceLevelMeter(
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
internal fun VoiceProcessingPanel() {
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
internal fun VoiceProcessingStep(
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
internal fun VoiceDraftChangePreviewRow(
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
internal fun VoiceDraftValueColumn(
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
internal fun VoiceDraftSourceHeader(
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
internal fun VoiceDraftSourceHeaderButton(
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
internal fun VoiceAssistSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun VoiceDraftMetadataRow(label: String, value: String) {
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

internal fun formatVoiceElapsedTenths(milliseconds: Long): String {
    val seconds = (milliseconds.coerceIn(0L, 29_900L).toDouble() / 1000.0)
    return String.format(Locale.US, "%.1fs", seconds)
}

internal fun String.toInstallSizeLabel(): String = replace(" ", "").lowercase(Locale.US)

@Composable
internal fun VoiceAssistBlock(title: String, body: String) {
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
