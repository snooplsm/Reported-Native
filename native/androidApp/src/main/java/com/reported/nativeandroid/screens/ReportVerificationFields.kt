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
internal fun VerifyMediaPanel(
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

internal enum class ReportTextEditTarget(
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
internal fun ReportLongTextSelectionField(
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
internal fun PhiladelphiaMobilityAccessFields(
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
internal fun ReportedAutocompleteField(
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
internal fun ReportedOptionsField(
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
internal fun VehicleLookupStatus(state: ComposerUiState) {
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

internal fun ComposerUiState.showsPhiladelphiaMobilityAccessFields(context: Context): Boolean =
    reportAddressProviderFor(
        context = context,
        latitude = latitude,
        longitude = longitude,
        address = addressQuery.ifBlank { address }
    ) == ReportAddressProvider.Philadelphia

@Composable
internal fun FullScreenReportTextEditor(
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
internal fun AddressField(
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
internal fun SubmitReportButton(
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
internal fun FieldErrorRow(errors: List<String?>) {
    val messages = errors.filterNotNull()
    if (messages.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        messages.forEach { message ->
            ValidationMessage(message)
        }
    }
}

@Composable
internal fun ValidationMessage(
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
