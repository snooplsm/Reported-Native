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
internal fun ComplaintTile(
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
internal fun ComplaintTileArt(
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
internal fun UploadTile(
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
internal fun PrimaryMediaPlaceholder(
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
internal fun LicensePlateBoundsOverlay(
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

internal fun plateCandidatePolygon(
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

internal fun List<PlateCandidate>.detectionSourceSize(): Size? =
    firstNotNullOfOrNull(::candidatesSourceSize)

internal fun candidatesSourceSize(candidate: PlateCandidate): Size? {
    val width = candidate.sourceImageWidth?.takeIf { it > 0 } ?: return null
    val height = candidate.sourceImageHeight?.takeIf { it > 0 } ?: return null
    return Size(width.toFloat(), height.toFloat())
}

internal fun PlateCandidate.hasUsableCornerPoints(): Boolean {
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

internal fun List<Offset>.inflateFromCentroid(amount: Float): List<Offset> {
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

internal fun List<Offset>.containsPoint(point: Offset): Boolean {
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
