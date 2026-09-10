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

@Composable
internal fun LiveIncidentPager(
    incidents: List<LiveIncidentRecord>,
    pendingDeleteIds: Set<Long>,
    onIncidentClick: (LiveIncidentRecord) -> Unit,
    onDeleteRequested: (LiveIncidentRecord) -> Unit,
    onDeleteUndo: (LiveIncidentRecord) -> Unit,
    onDeleteCommitted: (LiveIncidentRecord) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(incidents, key = { it.id }) { incident ->
            if (incident.id in pendingDeleteIds) {
                PendingDeleteIncidentCard(
                    incident = incident,
                    onUndo = { onDeleteUndo(incident) },
                    onTimeout = { onDeleteCommitted(incident) }
                )
            } else {
                LiveIncidentCard(
                    incident = incident,
                    onClick = { onIncidentClick(incident) },
                    onDeleteRequested = { onDeleteRequested(incident) }
                )
            }
        }
    }
}

@Composable
internal fun LiveIncidentCard(
    incident: LiveIncidentRecord,
    onClick: () -> Unit,
    onDeleteRequested: () -> Unit
) {
    var dragY by remember(incident.id) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .size(width = 280.dp, height = 116.dp)
            .offset { IntOffset(0, dragY.roundToInt()) }
            .pointerInput(incident.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragY = (dragY + dragAmount).coerceIn(-90f, 90f)
                    },
                    onDragEnd = {
                        if (abs(dragY) >= 44f) {
                            onDeleteRequested()
                        }
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f }
                )
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.62f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 82.dp, height = 58.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.12f)
            ) {
                if (incident.thumbnailUri != null) {
                    AsyncImage(
                        model = incident.thumbnailUri,
                        contentDescription = "Detected plate crop",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Plate", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${incident.plate} - ${incident.plateRegion}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = incident.complaintName,
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = incident.displayIncidentTime,
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (incident.videoUri != null) "Queued with clip" else "Queued",
                    color = Color(0xFFFF7A2F),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
internal fun PendingDeleteIncidentCard(
    incident: LiveIncidentRecord,
    onUndo: () -> Unit,
    onTimeout: () -> Unit
) {
    val alpha = remember(incident.id) { Animatable(1f) }
    LaunchedEffect(incident.id) {
        delay(4_500L)
        alpha.animateTo(0f, animationSpec = tween(durationMillis = 1_500))
        onTimeout()
    }
    Surface(
        modifier = Modifier
            .size(width = 280.dp, height = 116.dp)
            .graphicsLayer { this.alpha = alpha.value },
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.68f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Deleted",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${incident.plate} - ${incident.plateRegion}",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onUndo) {
                Text("Undo", color = Color(0xFFFF7A2F), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun LiveIncidentVideoPlayer(
    incident: LiveIncidentRecord,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoUri = incident.videoUri
    if (videoUri == null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "No clip saved for this item",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { it.player = player }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveIncidentDetailsSheet(
    incidents: List<LiveIncidentRecord>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIncidentChanged: (LiveIncidentRecord) -> Unit,
    onIncidentDeleted: (LiveIncidentRecord) -> Unit
) {
    val safeInitialIndex = initialIndex.coerceIn(0, incidents.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { incidents.size }
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Queued incidents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalPager(state = pagerState) { page ->
                val incident = incidents[page]
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiveIncidentVideoPlayer(
                        incident = incident,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                    LiveIncidentDetailSummary(
                        incident = incident,
                        onIncidentChanged = onIncidentChanged,
                        onIncidentDeleted = onIncidentDeleted
                    )
                }
            }
            Text(
                text = "${pagerState.currentPage + 1} of ${incidents.size}",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
internal fun LiveIncidentDetailSummary(
    incident: LiveIncidentRecord,
    onIncidentChanged: (LiveIncidentRecord) -> Unit,
    onIncidentDeleted: (LiveIncidentRecord) -> Unit
) {
    var plate by remember(incident.id, incident.plate) { mutableStateOf(incident.plate) }
    var plateRegion by remember(incident.id, incident.plateRegion) { mutableStateOf(incident.plateRegion) }
    var address by remember(incident.id, incident.address) { mutableStateOf(incident.address) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(width = 96.dp, height = 68.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (incident.thumbnailUri != null) {
                        AsyncImage(
                            model = incident.thumbnailUri,
                            contentDescription = "Detected plate crop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Plate", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = incident.complaintName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Plate ${(incident.plateConfidence * 100).toInt()}%  State ${((incident.stateConfidence ?: 0f) * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = incident.displayIncidentTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase().take(PlatePatternClassifier.MAX_LICENSE_PLATE_LENGTH) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Plate") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = plateRegion,
                    onValueChange = { plateRegion = it.uppercase().take(2) },
                    modifier = Modifier.width(112.dp),
                    label = { Text("State") },
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Address") },
                maxLines = 2
            )
            PrimaryButton(
                text = "Save Changes",
                onClick = {
                    onIncidentChanged(
                        incident.copy(
                            plate = plate.trim().uppercase(),
                            plateRegion = plateRegion.trim().uppercase(),
                            address = address.trim()
                        )
                    )
                },
                enabled = plate.isNotBlank() && plateRegion.isNotBlank() && address.isNotBlank()
            )
            TextButton(onClick = { onIncidentDeleted(incident) }) {
                Text("Discard Report", color = Color(0xFFFF7A2F), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun PermissionPrompt(modifier: Modifier = Modifier, onRequest: () -> Unit) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Camera permission is required for Live mode.", style = MaterialTheme.typography.titleMedium)
            PrimaryButton("Enable camera", onClick = onRequest)
        }
    }
}

internal fun ImageProxy.toBitmapSafely(): Bitmap? = runCatching {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, out)
    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return@runCatching null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) {
        bitmap
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}.getOrNull()
