package com.reported.nativeandroid.live

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import com.reported.nativeandroid.app.PlateCandidate
import com.reported.nativeandroid.live.LiveAction
import com.reported.nativeandroid.live.LiveIncidentRecord
import com.reported.nativeandroid.live.LiveRollingVideoRecorder
import com.reported.nativeandroid.live.LiveViewModel
import com.reported.nativeandroid.screens.AddressMapSheet
import com.reported.nativeandroid.screens.PrimaryButton
import com.reported.shared.model.PlatePatternClassifier
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    onOpenMenu: () -> Unit,
    vm: LiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var recorderKey by remember { mutableStateOf(0) }
    val rollingRecorder = remember(recorderKey) {
        LiveRollingVideoRecorder(
            context = context,
            mainExecutor = ContextCompat.getMainExecutor(context)
        )
    }
    var showMap by remember { mutableStateOf(false) }
    var showComplaintPicker by remember { mutableStateOf(false) }
    var reviewingReports by remember { mutableStateOf(false) }
    var selectedIncidentIndex by remember { mutableStateOf<Int?>(null) }
    val pendingDeleteIds = remember { mutableStateMapOf<Long, Boolean>() }
    val checkedIncidentIds = remember { mutableStateMapOf<Long, Boolean>() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onAction(LiveAction.CameraPermissionChanged(granted)) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.onAction(LiveAction.MicrophonePermissionChanged(granted))
        vm.onAction(LiveAction.RecordAudioChanged(granted))
    }

    LaunchedEffect(Unit) {
        vm.onAction(
            LiveAction.CameraPermissionChanged(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            )
        )
        vm.onAction(
            LiveAction.MicrophonePermissionChanged(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        )
    }

    LaunchedEffect(state.recordAudio, state.microphonePermissionGranted) {
        rollingRecorder.setAudioEnabled(state.recordAudio && state.microphonePermissionGranted)
    }

    LaunchedEffect(state.clipRequestId) {
        val requestId = state.clipRequestId ?: return@LaunchedEffect
        rollingRecorder.requestIncidentClip(requestId) { result ->
            if (result.uri != null) {
                vm.onAction(LiveAction.IncidentClipReady(requestId, result.uri))
            } else {
                vm.onAction(
                    LiveAction.IncidentClipFailed(
                        requestId = requestId,
                        message = result.error ?: "Incident queued without a video clip."
                    )
                )
            }
        }
    }

    DisposableEffect(rollingRecorder) {
        onDispose { rollingRecorder.shutdown() }
    }

    if (showMap) {
        AddressMapSheet(
            initialLatLng = LatLng(state.latitude, state.longitude),
            initialAddress = state.address,
            photoAddressSuggestion = null,
            onDismiss = { showMap = false },
            onLocationSettled = { suggestion ->
                vm.onAction(
                    LiveAction.LocationChanged(
                        address = suggestion.label,
                        latitude = suggestion.latitude,
                        longitude = suggestion.longitude
                    )
                )
            }
        )
    }

    if (showComplaintPicker) {
        ModalBottomSheet(onDismissRequest = { showComplaintPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Manual complaint", style = MaterialTheme.typography.titleLarge)
                state.complaintCategories.forEach { category ->
                    Text(
                        text = category.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.onAction(LiveAction.ComplaintChanged(category.id))
                                showComplaintPicker = false
                            }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.onAction(LiveAction.ClearError) },
            confirmButton = {
                TextButton(onClick = { vm.onAction(LiveAction.ClearError) }) {
                    Text("OK")
                }
            },
            title = { Text("Live") },
            text = { Text(message) }
        )
    }

    selectedIncidentIndex?.let { index ->
        if (state.queuedIncidents.isNotEmpty()) {
            LiveIncidentDetailsSheet(
                incidents = state.queuedIncidents,
                initialIndex = index,
                onDismiss = { selectedIncidentIndex = null },
                onIncidentChanged = { vm.onAction(LiveAction.UpdateIncident(it)) },
                onIncidentDeleted = {
                    selectedIncidentIndex = null
                    checkedIncidentIds.remove(it.id)
                    vm.onAction(LiveAction.DeleteIncident(it))
                }
            )
        }
    }

    LaunchedEffect(state.queuedIncidents.map { it.id }) {
        val selected = selectedIncidentIndex
        if (selected != null && state.queuedIncidents.getOrNull(selected) == null) {
            selectedIncidentIndex = state.queuedIncidents.lastIndex.takeIf { it >= 0 }
        }
        state.queuedIncidents.forEach { incident ->
            if (!checkedIncidentIds.containsKey(incident.id)) {
                checkedIncidentIds[incident.id] = true
            }
        }
        checkedIncidentIds.keys.toList()
            .filterNot { id -> state.queuedIncidents.any { it.id == id } }
            .forEach { checkedIncidentIds.remove(it) }
    }

    if (reviewingReports) {
        LiveReviewScreen(
            incidents = state.queuedIncidents,
            checkedIds = checkedIncidentIds,
            onBackToLive = {
                recorderKey += 1
                reviewingReports = false
            },
            onOpenMenu = onOpenMenu,
            onIncidentClick = { incident ->
                selectedIncidentIndex = state.queuedIncidents.indexOfFirst { it.id == incident.id }
                    .takeIf { it >= 0 } ?: 0
            },
            onDeleteIncident = { incident -> vm.onAction(LiveAction.DeleteIncident(incident)) },
            onSubmitChecked = {
                vm.onAction(
                    LiveAction.SubmitIncidents(
                        checkedIncidentIds.filterValues { it }.keys.toSet()
                    )
                )
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.cameraPermissionGranted) {
            LiveCameraPreview(
                modifier = Modifier.fillMaxSize(),
                candidates = state.plateCandidates,
                recordAudio = state.recordAudio && state.microphonePermissionGranted,
                rollingRecorder = rollingRecorder,
                onFrame = vm::analyzeBitmap
            )
        } else {
            PermissionPrompt(
                modifier = Modifier.align(Alignment.Center),
                onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        LiveTopBar(
            selectedComplaint = state.complaintCategories.firstOrNull { it.id == state.selectedComplaintId }?.name
                ?: "Choose complaint",
            address = state.address.ifBlank { "Pick location" },
            recordAudio = state.recordAudio,
            microphoneGranted = state.microphonePermissionGranted,
            processing = state.processing,
            onOpenMenu = onOpenMenu,
            onComplaintClick = { showComplaintPicker = true },
            onLocationClick = { showMap = true },
            onMicClick = {
                if (state.recordAudio) {
                    vm.onAction(LiveAction.RecordAudioChanged(false))
                } else if (state.microphonePermissionGranted) {
                    vm.onAction(LiveAction.RecordAudioChanged(true))
                } else {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        LiveDetectionHud(
            candidate = state.bestCandidate,
            complaint = state.complaintCategories.firstOrNull { it.id == state.selectedComplaintDetectedId }?.name,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(
                text = if (state.capturingIncidentClip) "Saving clip..." else "Mark incident",
                onClick = { vm.onAction(LiveAction.MarkIncident) },
                enabled = state.canQueueManualIncident && !state.capturingIncidentClip
            )
            LiveIncidentPager(
                incidents = state.queuedIncidents,
                pendingDeleteIds = pendingDeleteIds.keys,
                onIncidentClick = { incident ->
                    selectedIncidentIndex = state.queuedIncidents.indexOfFirst { it.id == incident.id }
                        .takeIf { it >= 0 } ?: 0
                },
                onDeleteRequested = { incident -> pendingDeleteIds[incident.id] = true },
                onDeleteUndo = { incident -> pendingDeleteIds.remove(incident.id) },
                onDeleteCommitted = { incident ->
                    pendingDeleteIds.remove(incident.id)
                    vm.onAction(LiveAction.DeleteIncident(incident))
                }
            )
            if (state.queuedIncidents.isNotEmpty()) {
                PrimaryButton(
                    text = "Review Reports",
                    onClick = {
                        vm.onAction(LiveAction.LoadAllIncidents)
                        rollingRecorder.shutdown()
                        reviewingReports = true
                    }
                )
            }
        }
    }
}
