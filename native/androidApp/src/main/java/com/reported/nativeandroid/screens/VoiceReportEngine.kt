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
import com.reported.nativeandroid.di.AppGraph
import com.reported.nativeandroid.di.MessageResolver
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

internal data class VoiceReportGemmaResult(
    val transcript: String?,
    val draft: VoiceReportDraft
)

internal data class VoiceReportContext(
    val currentDeviceTimeIso: String,
    val currentAddress: String?,
    val currentLatitude: Double?,
    val currentLongitude: Double?,
    val photoAddress: String?,
    val photoOccurredAtIso: String?,
    val imageVisualContext: String?,
    val currentOccurredAtIso: String?,
    val currentComplaintTitle: String?,
    val currentPlate: String?,
    val currentPlateRegion: String?,
    val currentDescription: String?,
    val currentNotes: String?
) {
    companion object {
        fun from(
            state: ComposerUiState,
            complaintOptions: List<ComplaintOption>,
            imageVisualContext: String? = null,
            resolvedCurrentAddress: String? = null
        ): VoiceReportContext {
            val currentAddress = cleaned(state.addressQuery.ifBlank { state.address })
                ?: cleaned(resolvedCurrentAddress)
            val usableCoordinates = isUsableCoordinate(state.latitude, state.longitude)
            val selectedComplaint = complaintOptions.firstOrNull { it.id == state.selectedComplaintId }
            return VoiceReportContext(
                currentDeviceTimeIso = OffsetDateTime.now(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                currentAddress = currentAddress,
                currentLatitude = state.latitude.takeIf { usableCoordinates },
                currentLongitude = state.longitude.takeIf { usableCoordinates },
                photoAddress = cleaned(state.photoAddressSuggestion?.label),
                photoOccurredAtIso = cleaned(state.photoOccurredAtIso),
                imageVisualContext = cleaned(imageVisualContext),
                currentOccurredAtIso = cleaned(state.occurredAtIso),
                currentComplaintTitle = cleaned(selectedComplaint?.title),
                currentPlate = cleaned(state.plate),
                currentPlateRegion = cleaned(state.plateRegion),
                currentDescription = cleaned(state.description),
                currentNotes = cleaned(state.notes)
            )
        }

        private fun cleaned(value: String?): String? = value
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        private fun isUsableCoordinate(latitude: Double?, longitude: Double?): Boolean {
            if (latitude == null || longitude == null) return false
            if (!latitude.isFinite() || !longitude.isFinite()) return false
            if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return false
            return abs(latitude) > 0.000001 || abs(longitude) > 0.000001
        }
    }

    fun toPromptBlock(): String {
        val lines = buildList {
            add("- currentDeviceTimeIso: $currentDeviceTimeIso")
            currentAddress?.let { add("- currentAddress: $it") }
            if (currentLatitude != null && currentLongitude != null) {
                add("- currentCoordinates: ${String.format(Locale.US, "%.6f, %.6f", currentLatitude, currentLongitude)}")
            }
            photoAddress?.let { add("- imageAddress: $it") }
            photoOccurredAtIso?.let { add("- imageOccurredAtIso: $it") }
            imageVisualContext?.let { add("- imageVisualContext: $it") }
            currentOccurredAtIso?.let { add("- currentOccurredAtIso: $it") }
            currentComplaintTitle?.let { add("- currentComplaint: $it") }
            currentPlate?.let { add("- currentPlate: $it") }
            currentPlateRegion?.let { add("- currentPlateState: $it") }
            currentDescription?.let { add("- currentDescription: $it") }
            currentNotes?.let { add("- currentNotes: $it") }
        }
        return lines.joinToString(separator = "\n")
    }
}

internal suspend fun resolveVoiceCurrentAddress(
    context: Context,
    state: ComposerUiState
): String? {
    val existingAddress = state.addressQuery.ifBlank { state.address }.trim()
    if (existingAddress.isNotBlank()) return null
    val latitude = state.latitude
    val longitude = state.longitude
    if (latitude == null || longitude == null) return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return null
    if (abs(latitude) <= 0.000001 && abs(longitude) <= 0.000001) return null
    return reverseGeocodeAddress(context, latitude, longitude)?.label
}

internal data class VoiceReportImageInput(
    val file: File,
    val deleteAfterUse: Boolean
)

internal fun resolveVoiceReportImageInput(
    context: Context,
    media: SubmissionMedia?
): VoiceReportImageInput? {
    if (media == null || media.isVideo) return null
    val uri = Uri.parse(media.uri)
    if (uri.scheme.equals("file", ignoreCase = true)) {
        val file = uri.path?.let(::File)
        if (file?.isFile == true && file.length() > 0L) {
            return VoiceReportImageInput(file = file, deleteAfterUse = false)
        }
    }

    val tempDirectory = File(context.cacheDir, "litertlm-images").apply { mkdirs() }
    val extension = media.displayName
        .substringAfterLast('.', "jpg")
        .replace(Regex("[^A-Za-z0-9]"), "")
        .ifBlank { "jpg" }
    val tempFile = File.createTempFile("reported_voice_image_", ".$extension", tempDirectory)
    return try {
        val input = context.contentResolver.openInputStream(uri)
        if (input == null) {
            tempFile.delete()
            return null
        }
        input.use { source ->
            tempFile.outputStream().use { target ->
                source.copyTo(target)
            }
        }
        if (tempFile.length() > 0L) {
            VoiceReportImageInput(file = tempFile, deleteAfterUse = true)
        } else {
            tempFile.delete()
            null
        }
    } catch (error: Throwable) {
        tempFile.delete()
        null
    }
}

internal object OnDeviceGemmaVoiceDraftEngine {
    val ModelDownloadSizeLabel: String
        get() = ReportedAiModelStore.DownloadSizeLabel

    fun isModelInstalled(context: Context): Boolean = ReportedAiModelStore.isModelInstalled(context)

    suspend fun downloadModel(
        context: Context,
        messages: MessageResolver = AppGraph.messages,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = ReportedAiModelStore.downloadModel(context, messages, onProgress)

    suspend fun generateDraft(
        context: Context,
        audioFile: File,
        imageFile: File? = null,
        complaintOptions: List<ComplaintOption>,
        voiceContext: VoiceReportContext
    ): Result<VoiceReportGemmaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val modelFile = ReportedAiModelStore.resolveModelFile(context)
                ?: error(
                    "REPORTED AI is not installed. Install REPORTED AI (${ReportedAiModelStore.compactDownloadSizeLabel}) before voice drafting can run."
                )
            val prompt = buildVoiceReportGemmaPrompt(complaintOptions, voiceContext)
            if (audioFile.length() <= VoiceReportAudioRecorder.WAV_HEADER_BYTES) {
                error("The recording was too short to process.")
            }

            try {
                generateDraftResponse(
                    context = context,
                    modelFile = modelFile,
                    audioFile = audioFile,
                    imageFile = imageFile,
                    prompt = prompt,
                    complaintOptions = complaintOptions
                )
            } catch (error: Throwable) {
                if (imageFile == null) throw error
                generateDraftResponse(
                    context = context,
                    modelFile = modelFile,
                    audioFile = audioFile,
                    imageFile = null,
                    prompt = prompt,
                    complaintOptions = complaintOptions
                )
            }
        }
    }

    suspend fun generateImageContext(
        context: Context,
        imageFile: File
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val modelFile = ReportedAiModelStore.resolveModelFile(context) ?: return@runCatching null
            if (!imageFile.isFile || imageFile.length() <= 0L) return@runCatching null
            createEngineWithFallback(
                context = context,
                modelFile = modelFile,
                visionEnabled = true,
                audioEnabled = false
            ).use { engine ->
                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 0.1,
                        temperature = 0.0
                    )
                )
                engine.createConversation(conversationConfig).use { conversation ->
                    val response = conversation.sendMessage(
                        Contents.of(
                            Content.ImageFile(imageFile.absolutePath),
                            Content.Text(
                                "Briefly inspect this report photo for form-filling context. Return one concise sentence with only clearly visible facts: possible complaint type, vehicle make/model/color/type, visible license plate text, location clues, and scene details. If uncertain, say uncertain rather than guessing."
                            )
                        )
                    )
                    response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = " ") { it.text }
                        .ifBlank { response.toString() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(1200)
                        .takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private suspend fun generateDraftResponse(
        context: Context,
        modelFile: File,
        audioFile: File,
        imageFile: File?,
        prompt: String,
        complaintOptions: List<ComplaintOption>
    ): VoiceReportGemmaResult {
        createEngineWithFallback(
            context = context,
            modelFile = modelFile,
            visionEnabled = imageFile != null,
            audioEnabled = true
        ).use { engine ->
            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = 1,
                    topP = 0.1,
                    temperature = 0.0
                )
            )
            engine.createConversation(conversationConfig).use { conversation ->
                val contents = buildList {
                    imageFile?.takeIf { it.isFile && it.length() > 0L }?.let {
                        add(Content.ImageFile(it.absolutePath))
                    }
                    add(Content.AudioFile(audioFile.absolutePath))
                    add(Content.Text(prompt))
                }
                val response = conversation.sendMessage(
                    Contents.of(contents)
                )
                val responseText = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "\n") { it.text }
                    .ifBlank { response.toString() }
                return resolveVoiceReportAddress(
                    context = context,
                    result = parseVoiceReportGemmaJson(responseText, complaintOptions)
                )
            }
        }
    }

    private fun createEngineWithFallback(
        context: Context,
        modelFile: File,
        visionEnabled: Boolean,
        audioEnabled: Boolean
    ): Engine {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir.orEmpty()
        val npuBackend = Backend.NPU(nativeLibraryDir = nativeLibraryDir)
        val attempts = listOf(
            VoiceBackendAttempt(
                label = "NPU",
                backend = npuBackend,
                visionBackend = npuBackend.takeIf { visionEnabled },
                audioBackend = npuBackend.takeIf { audioEnabled }
            ),
            VoiceBackendAttempt(
                label = "CPU",
                backend = Backend.CPU(),
                visionBackend = Backend.CPU().takeIf { visionEnabled },
                audioBackend = Backend.CPU().takeIf { audioEnabled }
            )
        )

        var lastFailure: Throwable? = null
        attempts.forEach { attempt ->
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = attempt.backend,
                visionBackend = attempt.visionBackend,
                audioBackend = attempt.audioBackend,
                cacheDir = ReportedAiModelStore.resolveCacheDir(context).absolutePath
            )
            val engine = Engine(engineConfig)
            try {
                engine.initialize()
                ReportedAiModelStore.setAccelerationStatus(
                    context,
                    if (attempt.label == "NPU") {
                        ReportedAiModelStore.AccelerationStatus.Accelerated
                    } else {
                        ReportedAiModelStore.AccelerationStatus.CpuFallback
                    }
                )
                Log.i(LogTag, "LiteRT-LM initialized with ${attempt.label} backend for ${modelFile.name}.")
                return engine
            } catch (error: Throwable) {
                runCatching { engine.close() }
                lastFailure = error
                Log.w(LogTag, "LiteRT-LM ${attempt.label} backend failed for ${modelFile.name}; trying fallback.", error)
            }
        }
        throw lastFailure ?: IllegalStateException("Could not initialize REPORTED AI.")
    }

    private data class VoiceBackendAttempt(
        val label: String,
        val backend: Backend,
        val visionBackend: Backend?,
        val audioBackend: Backend?
    )

    private const val LogTag = "ReportedAI"
}

internal suspend fun resolveVoiceReportAddress(
    context: Context,
    result: VoiceReportGemmaResult
): VoiceReportGemmaResult {
    val rawAddress = result.draft.address
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return result
    val resolvedAddress = resolveVoiceAddressLabel(context, rawAddress) ?: return result
    if (resolvedAddress.equals(rawAddress, ignoreCase = true)) return result
    return result.copy(draft = result.draft.copy(address = resolvedAddress))
}

internal suspend fun resolveVoiceAddressLabel(
    context: Context,
    rawAddress: String
): String? {
    parseVoiceCoordinate(rawAddress)?.let { (latitude, longitude) ->
        return reverseGeocodeAddress(context, latitude, longitude)?.label
    }
    val queries = voiceAddressSearchQueries(rawAddress)
    for (query in queries) {
        val suggestions = searchNycAddresses(query)
        val chosen = chooseVoiceAddressSuggestion(rawAddress, suggestions)
        if (chosen != null) return chosen.label
    }
    return null
}

internal fun parseVoiceCoordinate(rawAddress: String): Pair<Double, Double>? {
    val match = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")
        .find(rawAddress)
        ?: return null
    val latitude = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
    val longitude = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
    if (abs(latitude) > 90.0 || abs(longitude) > 180.0) return null
    return latitude to longitude
}

internal fun voiceAddressSearchQueries(rawAddress: String): List<String> {
    val cleaned = cleanVoiceAddressQuery(rawAddress)
    val variants = mutableListOf<String>()
    fun add(value: String?) {
        val normalized = value
            ?.trim(' ', ',', '.', ';')
            ?.replace(Regex("""\s+"""), " ")
            ?.takeIf { it.length >= 4 }
            ?: return
        if (variants.none { it.equals(normalized, ignoreCase = true) }) {
            variants += normalized
        }
    }
    add(cleaned)
    val houseStreetMatch = Regex("""\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\s+(.+)$""")
        .find(cleaned)
    if (houseStreetMatch != null) {
        val houseNumber = houseStreetMatch.groupValues[1]
        val street = houseStreetMatch.groupValues[2]
            .split(Regex("""\b(?:new\s+york|ny|usa|united\s+states|apt|apartment|unit|floor|fl)\b""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim(' ', ',', '.', ';')
        add("$houseNumber $street")
    }
    return variants
}

internal fun cleanVoiceAddressQuery(rawAddress: String): String =
    rawAddress
        .replace(Regex("""\b(?:address\s+is|address|near|at)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\b(?:new\s+york|nyc|ny|usa|united\s+states)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""[^\p{Alnum}\s-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

internal fun chooseVoiceAddressSuggestion(
    rawAddress: String,
    suggestions: List<AddressSuggestion>
): AddressSuggestion? {
    if (suggestions.isEmpty()) return null
    val expectedHouse = Regex("""\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\b""")
        .find(rawAddress)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase(Locale.US)
    val rawTokens = cleanVoiceAddressQuery(rawAddress)
        .lowercase(Locale.US)
        .split(Regex("""\s+"""))
        .filter { it.length >= 3 && !it.all(Char::isDigit) }
    fun score(suggestion: AddressSuggestion): Int {
        val label = suggestion.label.lowercase(Locale.US)
        var score = 0
        if (expectedHouse != null && expectedHouse in label) score += 6
        score += rawTokens.count { token -> token in label }
        if (suggestion.region == "NY") score += 1
        return score
    }
    return suggestions
        .maxByOrNull(::score)
        ?.takeIf { score(it) >= if (expectedHouse == null) 1 else 6 }
}
