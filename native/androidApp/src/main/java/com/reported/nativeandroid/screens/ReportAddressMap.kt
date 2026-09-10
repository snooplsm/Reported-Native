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
internal fun AddressMapTopBar(
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
internal fun AddressMapControls(
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
internal fun AddressMapStatus(
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
internal fun AddressMapCanvas(
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

internal fun addressCacheKey(latLng: LatLng): Pair<Long, Long> {
    val cellMeters = 3.048
    val metersPerLatDegree = 111_320.0
    val metersPerLngDegree = metersPerLatDegree * abs(cos(Math.toRadians(latLng.latitude))).coerceAtLeast(0.000001)
    return (latLng.latitude * metersPerLatDegree / cellMeters).roundToLong() to
        (latLng.longitude * metersPerLngDegree / cellMeters).roundToLong()
}
